# Task Manager Service

Task Manager Service is a console-based task management system built using Java.
It will allow the users to manage tasks.

Task Manager Service is a Java console application built with [Apache Maven](https://maven.apache.org/).

## Prerequisites

Before you begin, ensure you have the following installed on your system:

- **Java Development Kit (JDK)**: Version 17 or higher
- **Apache Maven**: Version 3.9.0 or higher [Setup Guide](https://maven.apache.org/install.html) OR `choco install maven`

## Steps to run the app:

Clone the app locally and run following commands in the terminal,

```bash
mvn clean package -DskipTests

java -cp target/task-manager-1.0-SNAPSHOT.jar com.airtribe.taskmanager.App
```
