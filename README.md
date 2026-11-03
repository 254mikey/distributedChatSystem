# Distributed Chat Application

## Requirements
- Java JDK 8 or higher
- No external libraries required

## installation steps on linux
sudo apt update
sudo apt install -y default-jdk

## confirm installation
java -version && javac -version

## Compilation
```bash
javac ChatServer.java ChatClient.java```

## Run it
- On different terminals

```bash
cd <your-project-folder>
java ChatServer 5000 Server-A
java ChatServer 5001 Server-B
java ChatClient
java ChatClient```


