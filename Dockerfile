FROM node:20-bookworm-slim AS frontend-build

WORKDIR /workspace/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build


FROM maven:3.9-eclipse-temurin-26 AS backend-build

WORKDIR /workspace

COPY . ./
COPY --from=frontend-build /workspace/frontend/dist ./frontend/dist
RUN mvn -pl application -am package -DskipTests


FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN apk add --no-cache \
        curl \
        font-dejavu \
        libreoffice-calc \
        libreoffice-impress \
        libreoffice-writer

COPY --from=backend-build /workspace/application/target/customer-service-chat-application-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
