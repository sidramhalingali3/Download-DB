# TiDB DB Downloader

A lightweight, beginner-friendly Spring Boot application designed to connect to a live **TiDB** or **MySQL-compatible** database, export a complete read-only SQL backup, and download it directly in the web browser.

---

## ⚠️ Security Warning

> [!CAUTION]
> **IMPORTANT:**  
> The `/download-db` endpoint exports the live database.  
> **Do not expose this application publicly without authentication and authorization.**

---

## 1. What This Project Does

* **Connects** to a live MySQL-compatible TiDB database using credentials supplied strictly via **environment variables**.
* **Exports** complete database schema structure, table data, triggers, and routines into a formatted `.sql` file using `mysqldump`.
* **Downloads** the `.sql` backup file automatically in your web browser with a timestamped filename (e.g. `tidb_backup_20260926_233000.sql`).
* **Safe & Read-Only**: Performs **ONLY** database read/export operations. It **NEVER** alters, updates, inserts, or deletes any data in your live database.
* **Credential Protection**: Database passwords and credentials are never logged, stored in Git, or exposed to the client interface.

---

## 2. Prerequisites & Required Tools

* **Java Version**: **Java 21** or higher.
* **Maven Version**: **Maven 3.8+** (or use the provided Maven Wrapper `./mvnw`).
* **MySQL Dump Tool**: `mysqldump` command line utility installed on the host machine.

---

## 3. How to Install & Check `mysqldump`

The application uses `mysqldump` under the hood to stream the database snapshot safely.

### Check if `mysqldump` is installed:

Open a terminal or command prompt and run:
```bash
mysqldump --version
```

### Installing `mysqldump`:

* **Windows**:
  * Install MySQL Server, MySQL Workbench, or MariaDB Client tools.
  * Add the `bin` folder containing `mysqldump.exe` (e.g., `C:\Program Files\MySQL\MySQL Server 8.0\bin`) to your system PATH.
  * Alternatively, set the environment variable `MYSQLDUMP_PATH` directly to `C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe`.

* **macOS**:
  ```bash
  brew install mysql-client
  export PATH="/opt/homebrew/opt/mysql-client/bin:$PATH"
  ```

* **Linux (Ubuntu / Debian)**:
  ```bash
  sudo apt-get update
  sudo apt-get install mysql-client
  ```

---

## 4. Environment Variables Configuration

The application reads all database connection parameters exclusively from environment variables for security.

### Required Environment Variables:

| Variable Name | Description | Default Value | Example |
|---|---|---|---|
| `DB_HOST` | TiDB server host/IP address | *(Required)* | `gateway01.ap-southeast-1.prod.aws.tidbcloud.com` |
| `DB_PORT` | TiDB server port | `4000` | `4000` |
| `DB_NAME` | Database name to export | *(Required)* | `myapp_db` |
| `DB_USERNAME` | Database username | *(Required)* | `2xxxxxxx.root` |
| `DB_PASSWORD` | Database password | *(Required)* | `your_secure_password` |
| `MYSQLDUMP_PATH` | Path to `mysqldump` executable | `mysqldump` | `mysqldump` or `C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe` |

### How to set environment variables before running:

#### On Windows (PowerShell):
```powershell
$env:DB_HOST="your-tidb-host.com"
$env:DB_PORT="4000"
$env:DB_NAME="your_db_name"
$env:DB_USERNAME="your_username"
$env:DB_PASSWORD="your_password"
$env:MYSQLDUMP_PATH="mysqldump"
```

#### On Windows (Command Prompt `cmd`):
```cmd
set DB_HOST=your-tidb-host.com
set DB_PORT=4000
set DB_NAME=your_db_name
set DB_USERNAME=your_username
set DB_PASSWORD=your_password
set MYSQLDUMP_PATH=mysqldump
```

#### On Linux / macOS (Bash / Zsh):
```bash
export DB_HOST="your-tidb-host.com"
export DB_PORT="4000"
export DB_NAME="your_db_name"
export DB_USERNAME="your_username"
export DB_PASSWORD="your_password"
export MYSQLDUMP_PATH="mysqldump"
```

---

## 5. How to Build & Run the Spring Boot Application

1. Open your terminal in the project directory:
   ```bash
   cd tidb-db-downloader
   ```

2. Compile and package the application using Maven:
   ```bash
   mvn clean package
   ```
   *(Or using Maven Wrapper: `./mvnw clean package` on Linux/macOS or `.\mvnw.cmd clean package` on Windows)*

3. Run the Spring Boot application:
   ```bash
   mvn spring-boot:run
   ```
   *(Or run the packaged JAR directly: `java -jar target/tidb-db-downloader-0.0.1-SNAPSHOT.jar`)*

4. The server will start on port `8080` (or `http://localhost:8080`).

---

## 6. How to Open the Webpage & Download DB

1. Open your browser and navigate to:
   ```text
   http://localhost:8080
   ```
2. You will see the **Live Database Backup** interface.
3. Click the **`[ Download DB ]`** button.
4. The status will display **`Creating database backup... Please wait...`** while `mysqldump` runs.
5. Once completed, your browser will automatically download a file named `tidb_backup_YYYYMMDD_HHmmss.sql`.

---

## 7. Project File Structure

```text
tidb-db-downloader/
├── pom.xml
├── .gitignore
├── README.md
└── src/
    └── main/
        ├── java/
        │   └── com/example/dbdownloader/
        │       ├── DbDownloaderApplication.java
        │       └── DatabaseController.java
        └── resources/
            ├── application.properties
            └── static/
                └── index.html
```

---

## 8. Troubleshooting & Common Issues

| Issue / Symptom | Possible Cause | Solution |
|---|---|---|
| **`Executable 'mysqldump' not found`** | `mysqldump` is not installed or not in PATH | Install `mysqldump` or set the `MYSQLDUMP_PATH` environment variable to the absolute executable path. |
| **`Access denied for user`** | Invalid `DB_USERNAME` or `DB_PASSWORD` | Verify your TiDB database credentials and set environment variables correctly. |
| **`Can't connect to MySQL server`** | Incorrect `DB_HOST` or `DB_PORT`, or firewall block | Verify host address and port 4000. Ensure your IP is allowed in TiDB Cloud IP Access List if applicable. |
| **`Unknown database`** | Invalid `DB_NAME` | Double-check `DB_NAME` spelling in environment variables. |
| **`Environment variables not set`** | Missing required env vars | Set `DB_HOST`, `DB_NAME`, `DB_USERNAME`, and `DB_PASSWORD` prior to starting the application. |
| **`Empty backup file generated`** | User lacks SELECT / SHOW VIEW privileges | Ensure the database user has READ/SELECT permissions on all tables. |
