FROM eclipse-temurin:21-jdk-jammy
RUN apt-get -y update && apt-get -y upgrade && apt-get install -y ffmpeg
COPY ./build/libs/LecRec-1.0-SNAPSHOT-all.jar /app/app.jar
WORKDIR /app
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]