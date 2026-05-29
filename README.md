# Adaptify

A Spring Boot web application that transforms & converts any file to any other file.

## Supported Output Formats

| Format | Type       | Notes                                  |
|--------|------------|----------------------------------------|
| JPEG   | Image      | Quality-aware compression              |
| PNG    | Image      | Lossless, supports transparency        |
| WebP   | Image      | Modern format (encoded as PNG on JDK)  |
| TIFF   | Image      | High-fidelity, large file size         |
| BMP    | Image      | Uncompressed bitmap                    |
| GIF    | Image      | Legacy format                          |
| SVG    | Vector     | Raster image embedded in SVG envelope  |
| TXT    | Text       | Plain text extraction via PDFBox       |
| HTML   | Web        | Styled HTML with preserved paragraphs  |
| DOCX   | Word       | Apache POI Word document               |

Multi-page PDFs → images are delivered as a **.zip** archive (one image per page).

## Quality Presets

| Quality  | DPI  | JPEG Compression | Use case              |
|----------|------|------------------|-----------------------|
| Draft    | 72   | 50%              | Previews, quick share |
| Standard | 150  | 75%              | General use           |
| High     | 200  | 90%              | Presentations         |
| Ultra    | 300  | 100%             | Print-ready output    |

---

## Local Development

### Prerequisites
- Java 17+
- Maven 3.8+

### Run
```bash
mvn spring-boot:run
```
Open http://localhost:8080

### Build JAR
```bash
mvn clean package -DskipTests
java -jar target/pdf-converter.jar
```

---

## Oracle Cloud VM Deployment

### 1. Prepare the VM (one-time)

```bash
# On the VM — create deploy directory
sudo mkdir -p /opt/pdf-converter
sudo chown ubuntu:ubuntu /opt/pdf-converter

# Install the systemd service
sudo cp pdf-converter.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable pdf-converter
```

### 2. Add GitHub Secrets

Go to **Settings → Secrets and variables → Actions** in your GitHub repo and add:

| Secret Name              | Value                                        |
|--------------------------|----------------------------------------------|
| `ORACLE_VM_HOST`         | Public IP of your Oracle Cloud VM            |
| `ORACLE_VM_USER`         | SSH username (usually `ubuntu` or `opc`)     |
| `ORACLE_SSH_PRIVATE_KEY` | Contents of your private key (`~/.ssh/id_rsa`) |

### 3. Push to main

```bash
git add .
git commit -m "Initial deploy"
git push origin main
```

The GitHub Actions workflow will:
1. Build the Spring Boot JAR
2. SCP it to `/opt/pdf-converter/pdf-converter.jar.new`
3. Stop the service, rotate the JAR, restart
4. Health-check `http://localhost:8080/` with auto-rollback on failure

### Rollback
If deployment fails, the previous JAR is automatically restored from `pdf-converter.jar.prev`.
Manual rollback:
```bash
sudo systemctl stop pdf-converter
cp /opt/pdf-converter/pdf-converter.jar.prev /opt/pdf-converter/pdf-converter.jar
sudo systemctl start pdf-converter
```

---

## Architecture

```
src/
├── main/
│   ├── java/com/zaheen/pdfconverter/
│   │   ├── PdfConverterApplication.java     # Entry point
│   │   ├── controller/
│   │   │   └── ConverterController.java     # GET / and POST /convert, /info
│   │   ├── service/
│   │   │   └── PdfConverterService.java     # All conversion logic
│   │   └── model/
│   │       ├── OutputFormat.java            # Enum of output formats
│   │       └── ConversionResult.java        # Conversion output wrapper
│   └── resources/
│       ├── templates/index.html             # Thymeleaf UI template
│       └── application.properties
└── .github/workflows/deploy.yml            # CI/CD pipeline
```

## Dependencies

- **Apache PDFBox 3** — PDF rendering and text extraction
- **Apache POI** — DOCX generation
- **Spring Boot 3.2** — Web framework
- **Thymeleaf** — Server-side templating
