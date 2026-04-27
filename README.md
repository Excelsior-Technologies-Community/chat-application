# Real-Time Chat & Group Calling App

A modern Android application that enables real-time 1-1 and group communication with integrated audio and video calling. Built using scalable architecture and real-time data handling.

---

## Features

### Chat System
- 1-1 and Group Messaging  
- Real-time message delivery using Firebase Realtime Database  
- Unread message tracking  
- Message status handling  

### Messaging Enhancements
- Pin important messages (visible to all members)  
- Unpin or replace pinned messages  
- Delete messages for everyone  

### Notifications
- Local notifications for new messages  
- Smart notification handling (anti-spam, avoids duplicates)  
- Background message alerts  

### Calling (WebRTC)
- Peer-to-peer audio and video calling  
- Group calling using mesh architecture  
- Dynamic participant join and leave  
- Mute, video toggle and speaker controls  
- Real-time connection state handling  

### Group Management
- Create and manage groups  
- Add and remove participants  
- Rename groups  
- Leave groups  
- Removed users retain chat history with read-only access  

### Admin Controls & Roles
- Multi-admin support with role-based permissions  
- Admin-controlled group description editing  
- Permission controls for:
  - Editing group info  
  - Inviting new members  

### Call History
- Tracks calls:
  - Completed  
  - Missed  
  - Rejected  
  - Busy  
- Displays call duration and type (audio/video)  
- Maintained per user with real-time updates  

### UI/UX
- Built using Jetpack Compose  
- Dynamic video grid layout  
- Smooth state-driven UI updates  
- Media indicators (mute, video off)  
- Material 3 design  

---

## Architecture

- MVVM + Clean Architecture  
- Separation of concerns  

---

## Tech Stack

- Kotlin  
- Jetpack Compose  
- Firebase Realtime Database  
- WebRTC  
- Coroutines & Flow  

---

## Future Improvements

- Push Notifications (FCM)  
- End-to-End Encryption  
- Active speaker detection  
- Improved network resilience  

---

# Author

Aarav Halvadiya  

GitHub: https://github.com/Aarav3325  
LinkedIn: https://www.linkedin.com/in/aaravhalvadiya  
