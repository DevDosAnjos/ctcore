# Imagem do back-end (#11). Configuração só por variável de ambiente (DB_HOST, POSTGRES_*, ...).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
# sed: checkout no Windows pode trazer mvnw com CRLF
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests && mv target/ctcore-backend-*.jar app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home ctcore && mkdir /logs && chown ctcore /logs
USER ctcore
WORKDIR /app
COPY --from=build /src/app.jar app.jar
ENV LOG_DIR=/logs
# 8080: API; 8081: gestão (health e métricas), não publicar para a internet.
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
RUN exit 1
