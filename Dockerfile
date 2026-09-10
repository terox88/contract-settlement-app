FROM node:22-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

FROM gradle:8.14-jdk21 AS backend
WORKDIR /app
COPY settings.gradle build.gradle ./
COPY backend ./backend
COPY --from=frontend /app/frontend/dist ./backend/src/main/resources/static
RUN gradle :backend:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/backend/build/libs/*.jar app.jar
RUN mkdir -p /data/documents
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
