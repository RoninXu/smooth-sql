# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot Maven project named "smooth-sql" using:
- Java 17
- Spring Boot 3.1.2
- Spring Web
- Spring Data JPA
- MySQL Database
- Spring Boot DevTools

## Development Commands

### Build and Run
```bash
mvn clean compile          # Compile the project
mvn spring-boot:run        # Run the application
mvn clean package          # Build JAR file
java -jar target/smooth-sql-0.0.1-SNAPSHOT.jar  # Run the JAR
```

### Testing
```bash
mvn test                   # Run all tests
mvn test -Dtest=ClassName  # Run specific test class
```

### Development Tools
- Application runs on http://localhost:8080
- DevTools enables automatic restart on code changes

## Project Structure

```
src/
├── main/
│   ├── java/com/smoothsql/
│   │   └── SmoothSqlApplication.java  # Main application class
│   └── resources/
│       └── application.yml           # Configuration
└── test/
    └── java/com/smoothsql/
        └── SmoothSqlApplicationTests.java
```

## Configuration

The application uses MySQL database. Database connection details are configured in `application.yml`:

### Database Setup
1. Install MySQL server
2. Create database: `CREATE DATABASE smoothsql;`
3. Update database credentials in `application.yml` if needed:
   - Default username: `root`
   - Default password: (empty)
   - Default database: `smoothsql`
   - Default port: `3306`

### Configuration File
The application uses YAML format (`application.yml`) for configuration, including:
- Server port configuration
- MySQL database connection
- JPA/Hibernate settings
- SQL logging configuration