# WePlan - The Group to-do app
 
WePlan is your all-in-one group task management solution. Streamline collaboration within your team, family, or friends with easy task creation, real-time group visibility, reminders, and seamless cloud sync. Enhance productivity and stay organized effortlessly – WePlan has you covered.
 
 
## Flow of the application
 
Here is a brief walkthrough of the features and each of the functionalities implemented in the application :
 
### Login Screen
![Login](https://i.imgur.com/ZUHtjhU.png)
 
The above mentioned screens highlight the login interface of the ToDo application where the user can sign-up using Google's Single Sign On (just need a google/gmail account)
 
 
### Home Screen - Day View
![Home - Day View](https://i.imgur.com/e987c8E.png)
 
The home screen contains two main view fragments - The Day View and The Week View
 
These are the features implemented in the Day View here :
 
- Ability to see current Day's due tasks (marked in yellow)
- Can click on the checkbox and mark task as complete (is synced across databases and the cloud so every user inside the group is notified of the change)
- Ability to filter through each of the groups that the user is a part of
- Once a Task is completed, it will show-up in the completed tab in the navigation button at the bottom
- Ability to add a new task from any of the views
- Ability to click on any existing task item to modify them
 
### Home Screen - Week View
![Home - Week View](https://i.imgur.com/ImT47ol.png)
 
The home screen contains two main view fragments - The Day View and The Week View
 
These are the features implemented in the Week View here :
 
- Same features as Day View, but with a few additions
- Here the user can cycle through the different weeks of the calendar and find over due tasks or the upcoming tasks and plan ahead. Overdue/current tasks will again show up in yellow. Upcoming tasks will be normal colored
- As seen in one of the screenshots one of the tasks a part of one of the groups in which the demo-er is part of, had been completed, so the user is getting a notification in real time
 
### Home Screen - Group View
![Groups](https://i.imgur.com/1ZfR4Eq.png)
 
The Groups tab is the last tab in the bottom suite of navigation buttons, it will show a new View in which users can add a new group or add existing members who have signed into the app previously as group members for a particular group
 
- Can add members, edit name and description
- All members of a particular group will be receiving notifications whenever someone modifies or completes a task within the group
 
 
### Task Addition
![TaskAdd](https://i.imgur.com/8yj6g8Y.png)
 
When in the Home Screen, from any of the views, a new task can be created
 
The following details are collected :
 
- Description of task
- Due date 
- Any specific notes 
- Reminder settings to be able to be reminded either during due time or a few minutes before the task is due
- Repetition/Frequency : To remind the user of a repeated task, we create multiple tasks till a specified end date
- Ability to either assign the task to "Self" or be a part of one of the Groups the user is a part of. If it is the latter, as discussed previously everyone gets notifications
- The user can also add images, either through the camera or by loading a file from the local storage library
 
 
### Task Edition
![TaskEdit](https://i.imgur.com/BHn97jP.png)
 
When in the Home Screen, from any of the views you can click on the specific task description and be able to view more details like images attached/other configurations
 
- All data is populated when clicking on a task description
- User can add task to a calendar even by using the icon - it results in opening up the calendar (Image 2, 3)
- User can replace data and even replace the image and notes
 
 
### How are we doing the notifications?
 
![Firebase](https://i.imgur.com/bFu2smf.png)
 
We have in place 7 total firebase cloud functions that are written in Node/Typescript (present in the files in the repository) - Note, we have covered up the URLs as they are public and exposed and we do not want to be billed for unauthorized use :)
 
- **fetchUpcomingReminders** : Will run every 15 minutes and fetches all the reminders that are present in our database for the next 30 mins, and schedules the respective notifications
- **frequencyTaskAdded** : When a task that needs repeated reminders (task with frequency of reminders) it is going to run and add the tasks according to the frequency and the end date. If end date is not given, we are using a specific default value for each category of Daily/Weekly/Monthly reminders
- **frequencyTaskDeleted** : When clicking on a task that has frequency setup, it deletes all the tasks that are repeated in the future, except the tasks that are marked as completed
- **onTasksChange** : Database listener that listens to firebase firestore collection of "tasks" - basically listens to any task lifecycle event to send notifications to all tasks
- **onUserAddedToGroup** : Sends a notification when a user is added to a group, to every existing user in the group and also the new user that is being added
- **onUserRemovedFromGroup** : Similar to the previous function, just that now it is for deletion of user from group
- **testFCM** : Created a dummy function to test - uses a task id to invoke a notification
 
 
 
 
 
 
## Authors
 
- [@Macbeth98 (Mani)](https://www.github.com/Macbeth98)
- [@UditSankhadasariya (Udit)](https://www.github.com/UditSankhadasariya)
- [@chesspatzer (Vish)](https://www.github.com/chesspatzer)
 
 
 
