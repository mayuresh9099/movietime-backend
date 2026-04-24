# 🎬 MovieTime Backend

A scalable backend system for an **Online Movie Ticket Booking Platform**, built using Spring Boot.  
This project demonstrates system design, authentication, and real-time booking concepts.

---

## 🚀 Features

- 🔐 JWT-based Authentication
- 🔑 Email & Password Login (secured with BCrypt)
- 🎟️ Movie Ticket Booking System
- 🪑 Seat Inventory Management
- 🎭 Theatre & Show Management
- ⚡ Kafka-based Event Processing (async workflows)
- 👥 Role-based Access (User, Admin, Theatre Owner)

---

## 🛠️ Tech Stack

- **Java 17**
- **Spring Boot**
- **Spring Security + JWT**
- **MySQL + Hibernate (JPA)**
- **Apache Kafka**
- **Maven**

---

## 🔐 Authentication

- User login via **email & password**
- Passwords are securely hashed using **BCrypt**
- JWT tokens are issued for authenticated sessions
- Stateless authentication for scalability

---

## 📦 Core Modules

- **Auth Service** – Login, registration, token handling
- **Booking Service** – Ticket booking & cancellation
- **Show Management** – Create/manage shows
- **Seat Inventory** – Real-time seat tracking

---

## ⚙️ Setup & Run

```bash
# Clone the repo
git clone https://github.com/mayuresh9099/movietime-backend.git

# Navigate to project
cd movietime-backend

# Run the application
mvn spring-boot:run