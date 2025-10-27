FROM gcr.io/distroless/java21-debian12
COPY ./Server/build/libs/LecRec-all.jar /app/app.jar
WORKDIR /app
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]