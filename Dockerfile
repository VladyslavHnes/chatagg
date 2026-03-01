FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache \
    tesseract-ocr \
    tesseract-ocr-data-eng \
    tesseract-ocr-data-ukr

RUN mkdir -p /app/photos /app/tdlib-session

WORKDIR /app

COPY backend/target/chatagg-1.0.0-SNAPSHOT.jar /app/chatagg.jar
COPY frontend/ /app/frontend/

EXPOSE ${APP_PORT:-7070}

ENTRYPOINT ["java", "-jar", "/app/chatagg.jar"]
