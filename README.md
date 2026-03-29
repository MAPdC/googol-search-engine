# 🌐 Googol - Distributed Search Engine

## 📝 About the Project
Googol is a distributed, highly available search engine inspired by major platforms. Developed as the final project for the Distributed Systems course at the University of Coimbra, it features an automated Web Crawler, an RMI-based distributed index storage, and a Spring Boot Web Interface.

## ✨ Key Features
- **Automated Web Crawling (Downloaders):** Concurrent crawlers built with Java and Jsoup to fetch and index web pages.

- **Distributed Storage (Barrels):** Fault-tolerant index storage using RPC/RMI and reliable multicast.

- **Web Interface:** A Spring Boot MVC application for users to search the index and submit new URLs.

- **Real-Time Analytics:** Live updates of active barrels, top searches, and response times using **WebSockets**.

## 🛠️ Tech Stack
- **Backend:** Java, Spring Boot, Java RMI (Remote Method Invocation)
- **Frontend:** HTML, Thymeleaf, WebSockets
- **Parsing:** Jsoup (v1.19.1)

## ⚙️ How to Run
This project uses **Maven** for dependency management. Ensure you have Java 17+ and Maven installed.

1. **Build the Project**
Open a terminal in the root directory of the project and compile the code:
```bash
mvn clean compile
```
2. **Start the RMI Registry**
In the same terminal, start the Java RMI registry (required for the distributed components to communicate):
```bash
# On Windows
start rmiregistry -J-Djava.class.path=target/classes

# On Linux/macOS
rmiregistry -J-Djava.class.path=target/classes &
```
3. **Start the Core Distributed Components**
Open separate terminal windows (or tabs) in the project root for each of the following components. We use `mvn exec:java` to automatically handle the classpath and dependencies (like Jsoup).
**Start the URL Queue:**
```bash
mvn exec:java -Dexec.mainClass="dei.googol.core.UrlQueue"
```
**Start the Storage Barrels:**
```bash
mvn exec:java -Dexec.mainClass="dei.googol.core.Barrel" -Dexec args="Barrel1"
# To start more barrels, run the command again changing "Barrel1" to "Barrel2", etc.
```
**Start the Web Crawlers (Downloaders):**
```bash
mvn exec:java -Dexec.mainClass="dei.googol.core.Downloader" -Dexec.args="Downloader1"
# To start more downloaders, change "Downloader1" to "Downloader2", etc.
```
4. **Start the Web Application (Spring Boot)**
Finally, in a new terminal, start the Spring Boot web interface:
```bash
mvn spring-boot:run
```
The application will be available at `http://localhost:8080`.

## 🚀 Roadmap & Future Enhancements
- [ ] **External API Integration:** Fetch related top stories from the Hacker News REST API.
- [ ] **AI Search Insights:** Generate contextual analysis for search results using the OpenAI API.
- [ ] **Code Refactoring:** Translate all legacy Portuguese variables and methods to English for standard compliance.