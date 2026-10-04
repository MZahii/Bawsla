# syntax=docker/dockerfile:1
# Image commune à tous les services Spring Boot.
# Usage : docker build --build-arg SERVICE=cours-service -f backend/Dockerfile backend
# Chaque service est un projet Maven autonome : seul son dossier est copié,
# donc modifier un service ne reconstruit pas les autres.

FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /src
COPY ${SERVICE}/ ./
# Cache ~/.m2 partagé entre les builds : les dépendances ne sont téléchargées qu'une fois.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -q -B package -DskipTests \
 && cp target/*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S bawsla && adduser -S bawsla -G bawsla
WORKDIR /app
COPY --from=build /app.jar app.jar
USER bawsla
# Mémoire modeste : 6 JVM tournent en parallèle sur un poste étudiant.
ENV JAVA_TOOL_OPTIONS="-Xms32m -Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
