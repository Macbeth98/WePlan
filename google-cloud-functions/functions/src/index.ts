/**
 * Import function triggers from their respective submodules:
 *
 * import {onCall} from "firebase-functions/v2/https";
 * import {onDocumentWritten} from "firebase-functions/v2/firestore";
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

import serviceAccount from './serviceAccountKey.json';

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount as admin.ServiceAccount),
  databaseURL: 'https://weplan-4fbf1-default-rtdb.firebaseio.com',
});

const sendNotification = async (task: FirebaseFirestore.QueryDocumentSnapshot) => {
  const taskData = task.data();
  if (!taskData) return;

  const taskId = task.id;

  const reminder = taskData.reminder.timestamp;

  const payload = {
    notification: {
      title: taskData.description,
      body: taskData.description,
    },
    data: {
      taskId: taskId,
      payloadType: 'TaskReminder',
    },
  };

  console.log(`Sending notification for task ${taskId} at ${reminder}`);
  console.log(`To subscribe Topic with task Id: ${taskId}`);

  let message = '';

  try {
    await Promise.all([
      admin.messaging().sendToTopic(taskId, payload),
      taskData.group_id ? admin.messaging().sendToTopic(taskData.group_id, payload) : null,
    ]);
    message = `Notification sent for task ${taskId} at ${reminder}, for group ${taskData.group_id}`;
  } catch (error) {
    message = `Error sending notification for task ${taskId}: ${error}`;
  }

  console.log(message);
  return message;
};

export const fetchUpcomingReminders = functions.pubsub.schedule('every 15 minutes').onRun(async (context) => {
  const db = admin.firestore();

  const NowInSeconds = Math.floor(new Date().getTime() / 1000);
  const nextFrequencyInSeconds = NowInSeconds + 30 * 60;

  const tasksSnapshot = await db
    .collection('tasks')
    .where('is_completed', '==', false)
    .where('reminder.timestamp', '>=', NowInSeconds)
    .where('reminder.timestamp', '<=', nextFrequencyInSeconds)
    .get(); // Get all tasks with a reminder set for today or tomorrow

  // Now Schedule a notification for each task to trigger at the reminder timestamp
  tasksSnapshot.forEach(async (doc) => {
    // Send notification for each task
    await sendNotification(doc);
  });
});

exports.testFCM = functions.https.onRequest(async (req, res) => {
  try {
    const topic = req.query.topic?.toString();

    if (topic) {
      console.log(`testFCM: Sending test notification to topic ${topic}`);
      const task = await admin.firestore().collection('tasks').doc(topic).get();
      const message = await sendNotification(task as FirebaseFirestore.QueryDocumentSnapshot);
      res.send(message);
      return;
    }

    const message = {
      notification: {
        title: 'Test Title',
        body: 'Test body',
      },
      topic: 'testTopic',
    };

    await admin.messaging().send(message);
    res.send('Notification sent successfully');
  } catch (error) {
    console.error('Error sending test notification:', error);
    res.status(500).send('Error sending test notification');
  }
});

const insertTaskFrequency = async (taskId: string, task: admin.firestore.DocumentData | null | undefined) => {
  if (!task) return;

  const db = admin.firestore();

  const frequency_id = task.frequency.id;

  console.log(`Inserting task ${taskId} with frequency id ${frequency_id}`);
  if (!frequency_id) {
    console.log(`No frequency id found for task ${taskId}`);
    return;
  }

  if (!task.original_source) {
    console.log(`Task ${taskId} is not a recurring task`);
    return;
  }

  const due_date = task.due_date;

  let reminder = task.reminder;

  const reminderOption = reminder.option;

  let reminderTimestamp = reminder.timestamp;

  const batch = db.batch();

  const end_date: string = task.frequency.end_date;
  const option: string = task.frequency.option;

  let recurringDurationInSeconds = 0;
  let daysInMonth;
  const oneDayInSeconds = 24 * 60 * 60;

  let instancesCount = 0;

  let dueDate = new Date(due_date);

  const month = dueDate.getMonth() + 1;

  const optionLCase = option.toLowerCase();

  let endDate = dueDate;

  if (end_date) endDate = new Date(end_date);

  if (optionLCase === 'daily') {
    recurringDurationInSeconds = 24 * 60 * 60;
    instancesCount = (endDate.getTime() / 1000 - dueDate.getTime() / 1000) / recurringDurationInSeconds;
    if (instancesCount <= 0) {
      instancesCount = 14;
    }
  } else if (optionLCase === 'weekly') {
    recurringDurationInSeconds = 7 * 24 * 60 * 60;
    instancesCount = (endDate.getTime() / 1000 - dueDate.getTime() / 1000) / recurringDurationInSeconds;
    if (instancesCount <= 0) {
      instancesCount = 4;
    }
  } else if (optionLCase === 'monthly') {
    recurringDurationInSeconds = 30 * 24 * 60 * 60;
    instancesCount = (endDate.getTime() / 1000 - dueDate.getTime() / 1000) / recurringDurationInSeconds;
    if (instancesCount <= 0) {
      instancesCount = 1;
    }
  } else {
    console.log(`Invalid option ${option} for task ${taskId}`);
    return;
  }

  while (dueDate <= endDate || instancesCount > 0) {
    daysInMonth = new Date(dueDate.getFullYear(), month, 0).getDate();

    let nextDueDate;
    if (option === 'monthly') {
      nextDueDate = new Date(dueDate.getTime() + daysInMonth * oneDayInSeconds * 1000);
      recurringDurationInSeconds = daysInMonth * oneDayInSeconds;
    } else {
      nextDueDate = new Date(dueDate.getTime() + recurringDurationInSeconds * 1000);
    }

    const nextReminder = reminder;

    if (reminderTimestamp && reminderOption != 'No Reminder') {
      const nextReminderTimestamp = reminderTimestamp + recurringDurationInSeconds;

      nextReminder.timestamp = nextReminderTimestamp;
      nextReminder.time = new Date(nextReminderTimestamp * 1000).toISOString();
    }

    const doc = db.collection('tasks').doc();

    batch.set(doc, {
      ...task,
      due_date: nextDueDate.toISOString(),
      reminder: { ...nextReminder },
      inserted_by: 'system',
      original_source: null,
    });

    dueDate = nextDueDate;
    reminder = { ...nextReminder };
    reminderTimestamp = nextReminder.timestamp;
    instancesCount--;
  }

  await batch.commit();

  console.log(`Inserted ${instancesCount} tasks for task ${taskId} with frequency id ${frequency_id}`);

  return;
};

const deleteTaskFrequency = async (taskId: string, frequency_id: string) => {
  const db = admin.firestore();
  console.log(`Deleting task ${taskId} with frequency id ${frequency_id}`);
  if (!frequency_id) {
    console.log(`No frequency id found for task ${taskId}`);
    return;
  }
  const tasksSnapshot = await db.collection('tasks').where('frequency.id', '==', frequency_id).get();
  if (tasksSnapshot.empty) {
    console.log(`No tasks found with frequency id ${frequency_id}`);
    return;
  }

  tasksSnapshot.forEach(async (doc) => {
    const taskData = doc.data();
    if (!taskData) return;

    const taskFrequency = taskData.frequency;

    if (taskFrequency.id === frequency_id && taskData.is_completed === false) {
      console.log(`Deleting task ${doc.id} with frequency id ${frequency_id}`);
      await db.collection('tasks').doc(doc.id).delete();
    }
  });
  return;
};

const getChangeTypeAndTask = async (taskId: string, change: functions.Change<functions.firestore.DocumentSnapshot>) => {
  let changeType;

  let task;

  if (!change.before.exists && change.after.exists) {
    console.log(`Insertion: Task ${taskId} was created`);
    changeType = 'insert';
    task = change.after.data();
  } else if (change.before.exists && !change.after.exists) {
    console.log(`Deletion: Task ${taskId} was deleted`);
    changeType = 'delete';
    task = change.before.data();
  } else if (change.before.exists && change.after.exists) {
    console.log(`Update: Task ${taskId} was updated`);
    changeType = 'update';
    task = change.after.data();
  }

  return { changeType, task };
};

const checkAndSendNotification = async (
  taskId: string,
  task: admin.firestore.DocumentData | null | undefined,
  change: functions.Change<functions.firestore.DocumentSnapshot>
) => {
  if (!task) return;

  const reminder = task.reminder.timestamp;

  if (reminder) {
    if (reminder < Math.floor(new Date().getTime() / 1000)) {
      console.log(`Reminder for task ${taskId} is in the past`);
      return;
    }

    const _15MinutesInSeconds = 15 * 60;

    const delay = reminder - Math.floor(new Date().getTime() / 1000);

    if (delay > _15MinutesInSeconds) {
      console.log(`Reminder for task ${taskId} is more than 15 minutes away`);
      return;
    }

    await sendNotification(change.after as FirebaseFirestore.QueryDocumentSnapshot);

    console.log(`Notification scheduled for task ${taskId} at ${reminder}`);
  } else {
    console.log(`No reminder set for task ${taskId}`);
  }
};

const sendTaskLifeCycleNotification = async (
  taskId: string,
  change: functions.Change<functions.firestore.DocumentSnapshot>
) => {
  const { changeType, task } = await getChangeTypeAndTask(taskId, change);

  if (!task) return;

  const taskData = task;

  if (!taskData) return;

  const groupId = taskData.group_id;

  if (!groupId) return;

  const group = await admin.firestore().collection('groups').doc(groupId).get();

  if (!group.exists) return;

  const groupData = group.data();

  if (!groupData) {
    console.log(`No group data found for group ${groupId}`);
    return;
  }

  if (taskData.inserted_by === 'system') {
    console.log(`Task ${taskId} was inserted by system`);
    return;
  }

  const user = await admin.firestore().collection('users').doc(taskData.assigned_to).get();

  if (!user.exists) return;

  const userData = user.data();

  if (!userData) {
    console.log(`No user data found for user ${taskData.assigned_to}`);
    return;
  }

  if (changeType === 'insert') {
    const payloadGroup = {
      notification: {
        title: `New Task Added To ${groupData.name} by ${userData.name}`,
        body: `${taskData.description} has been added to the Group: ${groupData.name}`,
      },
      data: {
        groupId: groupId,
        taskId: taskId,
        assigned_to: taskData.assigned_to,
        payloadType: 'TaskLifeCycle',
        changeType: changeType,
      },
    };

    await admin.messaging().sendToTopic(groupId, payloadGroup);

    console.log(`Notification sent for group ${groupId} about new task ${taskId} being added.`);
  } else if (changeType === 'delete') {
    const payloadGroup = {
      notification: {
        title: `Task Removed From ${groupData.name} (Added by ${userData.name})`,
        body: `${taskData.description} has been removed from the Group: ${groupData.name}`,
      },
      data: {
        groupId: groupId,
        taskId: taskId,
        assigned_to: taskData.assigned_to,
        payloadType: 'TaskLifeCycle',
        changeType: changeType,
      },
    };

    await admin.messaging().sendToTopic(groupId, payloadGroup);

    console.log(`Notification sent for group ${groupId} about task ${taskId} being removed.`);
  } else if (changeType === 'update') {
    let title = `Task Updated In ${groupData.name} (Added by ${userData.name})`;
    let body = `${taskData.description} has been updated in the Group: ${groupData.name}`;

    const is_completed = taskData.is_completed;

    if (is_completed) {
      title = `Task Completed In ${groupData.name} (Added by ${userData.name})`;
      body = `${taskData.description} has been completed in the Group: ${groupData.name}`;
    }

    const payloadGroup = {
      notification: {
        title,
        body,
      },
      data: {
        groupId: groupId,
        taskId: taskId,
        assigned_to: taskData.assigned_to,
        payloadType: 'TaskLifeCycle',
        changeType: changeType,
      },
    };

    await admin.messaging().sendToTopic(groupId, payloadGroup);

    console.log(`Notification sent for group ${groupId} about task ${taskId} being updated.`);
  }

  return;
};

export const onTasksChange = functions.firestore.document('tasks/{taskId}').onWrite(async (change, context) => {
  const taskId = context.params.taskId;

  const task = change.after.exists ? change.after.data() : null;

  if (task && task.inserted_by === 'system') {
    console.log(`Task ${taskId} was inserted by system`);
    return;
  }

  await sendTaskLifeCycleNotification(taskId, change);

  await checkAndSendNotification(taskId, task, change);

  // if (task && task.original_source && !(task.inserted_by === 'system')) {
  //   await handleTaskFrequency(taskId, change);
  // } else {
  //   console.log(`Task ${taskId} is not a recurring task`);
  // }

  return;
});

export const onUserAddedToGroup = functions.https.onRequest(async (req, res) => {
  try {
    const userId = req.query.userId?.toString();
    const groupId = req.query.groupId?.toString();

    if (!userId || !groupId) {
      res.status(400).send('Missing userId or groupId');
      return;
    }

    const user = await admin.firestore().collection('users').doc(userId).get();

    if (!user.exists) {
      res.status(404).send('User not found');
      return;
    }

    const userData = user.data();

    if (!userData) {
      res.status(500).send('Error fetching user data');
      return;
    }

    const group = await admin.firestore().collection('groups').doc(groupId).get();

    if (!group.exists) {
      res.status(404).send('Group not found');
      return;
    }

    const payloadGroup = {
      notification: {
        title: 'New User Added To Group: ' + group.data()?.name || 'New User Added To Group',
        body: `${userData.name} has joined the group`,
      },
      data: {
        groupId: groupId,
        userId: userId,
        onUserAddedToGroup: 'true',
        payloadType: 'HandleGroup',
      },
    };

    await admin.messaging().sendToTopic(groupId, payloadGroup);

    console.log(`Notification sent for group ${groupId} about new user ${userId} joining.`);

    const payloadUser = {
      notification: {
        title: 'You have been added to a new group',
        body: `You have been added to the group ${group.data()?.name}`,
      },
      data: {
        groupId: groupId,
        userId: userId,
        onUserAddedToGroup: 'true',
        payloadType: 'HandleUser',
      },
    };

    await admin.messaging().sendToTopic(userId, payloadUser);

    console.log(`Notification sent for user ${userId} about joining group ${groupId}.`);

    res.send('Notification sent successfully');
  } catch (error) {
    console.error('Error sending UserGroup notification:', error);
    res.status(500).send('Error sending User Group notification');
  }
});

export const onUserRemovedFromGroup = functions.https.onRequest(async (req, res) => {
  try {
    const userId = req.query.userId?.toString();
    const groupId = req.query.groupId?.toString();

    if (!userId || !groupId) {
      res.status(400).send('Missing userId or groupId');
      return;
    }

    const user = await admin.firestore().collection('users').doc(userId).get();

    if (!user.exists) {
      res.status(404).send('User not found');
      return;
    }

    const userData = user.data();

    if (!userData) {
      res.status(500).send('Error fetching user data');
      return;
    }

    const group = await admin.firestore().collection('groups').doc(groupId).get();

    if (!group.exists) {
      res.status(404).send('Group not found');
      return;
    }

    const payloadGroup = {
      notification: {
        title: 'User Removed From Group: ' + group.data()?.name || 'User Removed From Group',
        body: `${userData.name} has left the group`,
      },
      data: {
        groupId: groupId,
        userId: userId,
        onUserRemovedFromGroup: 'true',
        payloadType: 'HandleGroup',
      },
    };

    await admin.messaging().sendToTopic(groupId, payloadGroup);

    console.log(`Notification sent for group ${groupId} about user ${userId} leaving.`);

    const payloadUser = {
      notification: {
        title: 'You have been removed from a group',
        body: `You have been removed from the group ${group.data()?.name}`,
      },
      data: {
        groupId: groupId,
        userId: userId,
        onUserRemovedFromGroup: 'true',
        payloadType: 'HandleUser',
      },
    };

    await admin.messaging().sendToTopic(userId, payloadUser);

    console.log(`Notification sent for user ${userId} about leaving group ${groupId}.`);

    res.send('Notification sent successfully');
  } catch (error) {
    console.error('Error sending UserGroup notification:', error);
    res.status(500).send('Error sending User Group notification');
  }
});

export const frequencyTaskAdded = functions.https.onRequest(async (req, res) => {
  const taskId = req.query.taskId?.toString();

  console.log(`Inserting task ${taskId} with frequency`);

  if (!taskId) {
    console.log(`No taskId found`);
    res.status(400).send('Missing taskId');
    return;
  }

  const task = await admin.firestore().collection('tasks').doc(taskId).get();

  if (!task.exists) {
    console.log(`No task found with taskId ${taskId}`);
    res.status(404).send('Task not found');
    return;
  }

  const taskData = task.data();

  if (!taskData) {
    console.log(`No task data found for taskId ${taskId}`);
    res.status(500).send('Error fetching task data');
    return;
  }

  const frequency_id = taskData.frequency.id;

  if (!frequency_id) {
    console.log(`No frequency id found for task ${taskId}`);
    res.status(400).send('Missing frequency_id');
    return;
  }

  if (!taskData.original_source) {
    console.log(`Task ${taskId} is not a recurring task`);
    res.status(400).send('Task is not a recurring task: original_source not found');
    return;
  }

  console.log(`Inserting task ${taskId} with frequency id ${frequency_id}`);
  await insertTaskFrequency(taskId, taskData);

  console.log(`Inserted task ${taskId} with frequency id ${frequency_id}`);

  res.send('Inserted task with frequency: ' + frequency_id + ' for task: ' + taskId);
});

export const frequencyTaskDeleted = functions.https.onRequest(async (req, res) => {
  const taskId = req.query.taskId?.toString();
  const frequency_id = req.query.frequency_id?.toString();

  console.log(`Deleting task ${taskId} with frequency id ${frequency_id}`);

  if (!taskId) {
    console.log(`No taskId found`);
    res.status(400).send('Missing taskId');
    return;
  }

  if (!frequency_id) {
    console.log(`No frequency id found for task ${taskId}`);
    res.status(400).send('Missing frequency_id');
    return;
  }

  console.log(`Deleting task ${taskId} with frequency id ${frequency_id}`);

  await deleteTaskFrequency(taskId, frequency_id);

  console.log(`Deleted task ${taskId} with frequency id ${frequency_id}`);

  res.send('Deleted task with frequency: ' + frequency_id + ' for task: ' + taskId);
});

// const handleTaskFrequency = async (taskId: string, change: functions.Change<functions.firestore.DocumentSnapshot>) => {
//   const { changeType, task } = await getChangeTypeAndTask(taskId, change);

//   if (!task) return;

//   const frequency_id = task.frequency.id;

//   if (!frequency_id) {
//     console.log(`No frequency id found for task ${taskId}`);
//     return;
//   }

//   if (changeType === 'delete') {
//     console.log(`Deleting task ${taskId} with frequency id ${frequency_id}`);
//     await deleteTaskFrequency(taskId, frequency_id);
//   }

//   if (changeType === 'insert') {
//     console.log(`Inserting task ${taskId} with frequency id ${frequency_id}`);
//     await insertTaskFrequency(taskId, task);
//   }

//   if (changeType === 'update') {
//     console.log(`Updating task ${taskId} with frequency id ${frequency_id}`);

//     const taskBefore = change.before.data();

//     if (!taskBefore) return;

//     const frequencyBefore = taskBefore.frequency;

//     const frequency_id_before = frequencyBefore.id;

//     const due_date_before = taskBefore.due_date;

//     const reminder_before = taskBefore.reminder;

//     const reminderOptionBefore = reminder_before.option;

//     const reminderTimestampBefore = reminder_before.timestamp;

//     const taskAfter = change.after.data();

//     if (!taskAfter) return;

//     const frequencyAfter = taskAfter.frequency;

//     const frequency_id_after = frequencyAfter.id;

//     const due_date_after = taskAfter.due_date;

//     const reminder_after = taskAfter.reminder;

//     const reminderOptionAfter = reminder_after.option;

//     const reminderTimestampAfter = reminder_after.timestamp;

//     if (
//       frequency_id_before === frequency_id_after &&
//       due_date_before === due_date_after &&
//       reminderOptionBefore === reminderOptionAfter &&
//       reminderTimestampBefore === reminderTimestampAfter
//     ) {
//       console.log(`No change in due date or reminder for task ${taskId}`);
//       return;
//     }

//     if (!frequency_id_before) {
//       console.log(`No frequency id found for task ${taskId}`);
//       if (reminderOptionAfter !== 'No Reminder') {
//         await insertTaskFrequency(taskId, taskAfter);
//       }
//       return;
//     }

//     await deleteTaskFrequency(taskId, frequency_id_before);

//     if (reminderOptionAfter !== 'No Reminder') {
//       await insertTaskFrequency(taskId, taskAfter);
//     }

//     return;
//   }
// };
