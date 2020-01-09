# Web Security Assignment

## About the application
This is a simple web application written in Kotlin + Ktor, which means it compiles into a single JAR file that runs the entire web server.

The app allows users to upload images and leave comments. If the uploader marks the upload as "private", the image should only be accessible to people with access to the image link.

However, the application is not quite well-written as it contains several security vulnerabilities.

If you've never used Kotlin before, you can do a quick tutorial here to get used to it: [Kotlin Koans](https://play.kotlinlang.org/koans/overview)

The intention of the exercise is to find the vulnerabilities in this code using automated tools and, if necessary, manually reading through the code.

## Required tools
The exercise should build into a Docker container, so you will probably need to install Docker on your computer. You can read how to do that here: [Get Docker](https://docs.docker.com/get-docker/).
The application's dependencies are managed using [Maven](https://maven.apache.org/). If you want to run the application outside Docker (for example, for development), you will probably need to install it.

## Recommended tools
As for development, any editor will do. Recommended are IntelliJ IDEA ([Free version](https://www.jetbrains.com/idea/) or [ultimate (free for students)](https://www.jetbrains.com/student/)) with the Kotlin plugin. Visual Studio Code should also work fine with the [Kotlin](https://marketplace.visualstudio.com/items?itemName=mathiasfrohlich.Kotlin) extension and the [Maven](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-maven) extension.

To scan for vulnerabilities, you can use [ZAP](https://www.owasp.org/index.php/OWASP_Zed_Attack_Proxy_Project). Read through the [documentation](https://github.com/zaproxy/zaproxy/releases/download/v2.8.0/ZAPGettingStartedGuide-2.8.pdf) if you don't know how to get started with that. 

## How to run the application
### Development
#### Installing maven dependencies
Your IDE may offer to sync Maven dependencies for you. If it does not, or you do not use an IDE, you can install the necessary dependencies as follows:
1. Install Maven on your system
2. Open a terminal window and navigate to the application directory
3. Run the following command:  
```
mvn install
```

#### Running a database
The application uses postgres as a database backend. To run it, either install postgres on your machine or use a Docker image:
```
docker run --name database -e POSTGRES_PASSWORD=mysecretpassword -p 5432:5432 -d postgres
```

#### Database configuration
By default, the application will try to connect to `production-postgres` with user `user`, password `testing-password` and database `production`.
You can configure this behaviour by setting the right environment variables.
The following environment variables are used to configure the database for the application:
```
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD
POSTGRES_HOST
```

#### Image storage

By default, images will be stored in `/var/tmp/images/`, but you can set a different directory by setting the `IMAGE_DIR` environment variable to a different directory.

#### Running the application
If you're using IntelliJ IDEA, add a new Kotlin run configuration for the following class: `nl.utwente.softwaresecurity.ApplicationKt`.
You can run or debug the application using the buttons in the run toolbar in the top of the window.

If you're running from the command line, build the application with the command:
```
mvn package
```
Then run the application with the command:
```
java -jar target/image-gallery-0.0.1-jar-with-dependencies.jar
```
To specify environment variables when running the application, you can configure it like such:
```
POSTGRES_USER=postgres POSTGRES_PASSWORD=mysecretpassword POSTGRES_HOST=10.1.1.1 POSTGRES_DB=postgres java -jar target/image-gallery-0.0.1-jar-with-dependencies.jar
```

### Building the docker image
To build the docker image, open a terminal and navigate to the project folder. From there, issue the following command to start building an image:
```
docker build -t software-security/imagegallery:latest .
```
To run the image, issue the following command:
```
docker run -e POSTGRES_HOST=<your computer's IP> -e POSTGRES_PASSWORD=<your postgres password> -e POSTGRES_DB=postgres -p 5000:5000 software-security/imagegallery:latest
```

#### Building and running using docker-compose
For this project we have included an [docker-compose](https://docs.docker.com/compose/) file to simplify the building and running of the docker images. Using docker-compose 
is not mandatory for this assignment, but it makes building and running the images a bit easier. The `docker-compose.yaml` file contains the configuration of the application 
container and the database container. For a full overview of what is possible using docker-compose files we refer to the [documentation](https://docs.docker.com/compose/compose-file/). 

To build and run the images, issue the following command:
```
docker-compose up --build
```
Make sure to add the `--build` flag to ensure that the image uses the last (saved) version of the source code.

To run the containers in the background, add the detached mode flag (`-d`) to the up command:
```
docker-compose up --build -d
```

In the foreground mode, we can stop the images from running by sending a SIGINT to the containers (`Ctrl+C` on most systems). In detached mode, we can stop the images by
issuing the following command:
```
docker-compose down
```


### Managing the database
If you want to inspect the database, you can install tools like [pgAdmin](https://www.pgadmin.org/) to visually browse and edit the database.
