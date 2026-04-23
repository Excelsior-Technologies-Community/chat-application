# Real-Time Chat & Group Calling App

A modern Android application that enables real-time 1-1 and group communication with integrated audio and video calling. Built using scalable architecture and real-time data handling.

---

## Features

### Chat System
- 1-1 and Group Messaging  
- Real-time message delivery using Firebase Realtime Database  
- Unread message tracking  
- Message status handling  

### Calling (WebRTC)
- Peer-to-peer audio and video calling  
- Group calling using mesh architecture  
- Dynamic participant join and leave  
- Mute, video toggle, and speaker controls  
- Real-time connection state handling  

### Group Management
- Create and manage groups  
- Add and remove participants  
- Rename group  
- Leave group functionality  
- Removed users retain chat history (read-only access)  

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
- Kotlin Flow / StateFlow for reactive UI  
- Repository pattern  
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
