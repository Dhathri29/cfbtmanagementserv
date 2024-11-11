![](RaptorLogo.png)

This project was created using the maven archetype provided by the Raptor framework team. The project contains a simple example of a resource.

Before you start the development of your own service, you need to understand how this archetype works and also know how to clean it up.

This README discusses the archetype and also provides a list of the cleanups that you probably have to do to remove some of the code generated.


Starting on version 3.0.0, Raptor applications are also Spring Boot applications. It is recommended that you get familiarized with Spring Boot to take the best advantage of Raptor framework.

Suggested Spring Boot references:

1. [Spring Boot website](http://projects.spring.io/spring-boot/)
1. [Spring Boot Reference Guide](http://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/)
1. [Getting Started with Spring Boot](https://www.youtube.com/watch?v=sbPSjI4tt10)


The archetype results in a _hello world_ sample application. This is a simple example of how you can create a REST API using Raptor and also as a way to give you a good project structure.

The example in the archetype is called SampleResource and it is simply producing the message "Hello World!" when you make a HTTP request to a specific URL. 


The project is organized as a multi-pom maven project. There are 4 _pom_ files:

- The root pom
  - Specifies the submodules
  - Sets up some common properties and dependencies for the submodules
- The API pom
  - Defines the submodule for your API
- The implementation pom
  - Defines the submodule for the implementation project
- The functional test pom
  - Defines some functional tests for the sample resource

The separation of API and the implementation project is a recommended structure that makes it possible for potential clients of your service to use the API generated JAR file to setup proxies whereas the implementation project is local to the deployment of the service.


The API project holds the DTO's (see https://en.wikipedia.org/wiki/Data_transfer_object), the REST API and the JAX-RS 2.0 mapping (see: https://jax-rs-spec.java.net/).

A client may decide to include this project to avoid some coding (in particular, the model objects or DTO's may be useful to the clients).

We strongly suggest that you keep this strategy for your service.


The implementation project contains the implementation of the service. 


This project provides examples for how you can use the framework to run functional tests.

This project is devrunner enabled, which means to run your functional tests, all you need to enter is:
dr functional-test --stagename localhost
To install and learn more about devrunner, please see http://devrunner.

This project also includes time bound test suites (small.xml, medium.xml, large.xml and all.xml) which are provided for you to manage your tests and adhering to ECI (Enterprise Continuous Integration) compliance.
Please see http://eci for more information.


For running security scans with DevRunner, please see  https://go/dr-security for more information.


This performance test folder includes a sample performance test (samplePerformanceTest.jmx) that you can run via [PTaaS](https://engineering.paypalcorp.com/ptaas/execute). 

More details on PTaaS can be found [here](https://engineering.paypalcorp.com/ptaas/).


You must build the resource using your IDE or maven from the command line prior to starting the applications to prevent startup issues.

You can build the project from the commandline by navigating to the root directory and invoking `mvn -U clean install`


There are two ways to start the application:

1. From a terminal, go to the `Service` project, and run `mvn spring-boot:run`
1. From your favorite IDE run the entry point (the main method) in the `RaptorApplication` class, which is under the `init` package in your Service project.

This is a Spring Boot application, and it contains an embedded servlet container. The application deliverable is a JAR file, instead of a WAR file, so you do NOT need a regular standalone container.


You should be able to test the sample resource by accessing: `https://{hostname}:8443/v1/{appName}/sampleresource/hello` or `http://{hostname}:8080/v1/{appName}/sampleresource/hello` or `http://{hostname}:8083/v1/{appName}/sampleresource/hello`

For example, assuming you're running your project locally (`hostname=localhost`) and that you named your project _myproject_ (`appName=myproject`), you can read the sample resource by opening your browser on:

`https://localhost:8443/v1/myproject/sampleresource/hello` or `http://localhost:8080/v1/myproject/sampleresource/hello` or `http://localhost:8083/v1/myproject/sampleresource/hello`



In this section we'll focus on the most frequently asked questions around the archetype and provide a guided walkthrough of the code that makes up the sample resource.

We assume that you already know some of the fundamental technologies used to implement the resource (Java, JEE, Spring framework, Spring Boot, JAX-RS 2.0, etc).


As said earlier, this is a Spring Boot application, which means Spring is already integrated with your application natively.

Also, because the servlet container is embedded, you will not find any `web.xml` file. They are not necessary in embedded containers.

Regarding your application Spring Beans, notice that the project also does not have any Spring XML configuration file. Instead of that, all your Spring beans are defined via annotations (see `SampleResourceImpl.java` class as an example).

All Raptor framework Spring beans are already defined and loaded behind the scenes, so there is nothing you need to do in that regard neither.

If you have Spring beans in your application that are not under the package you defined in the archetype, then you can specify them to be scanned by adding extra packages to the `ComponentScan` annotation in `RaptorApplication.java` class. Notice that you will have to do so by using an array, like this:

```java
@ComponentScan({"com.testapp.impl", "com.testapp.otherpackage"})
```

In case you prefer to define your Spring beans via XML file (for example if you are migrating from Raptor 2 and prefer to keep the XML files) then you can also use the `RaptorApplication.java` class to add a Spring annotation `org.springframework.context.annotation.ImportResource`. [See further details here](http://docs.spring.io/autorepo/docs/spring/4.2.3.RELEASE/javadoc-api/org/springframework/context/annotation/Configuration.html) (*search for "ImportResource"*).


Now that we understand how we use Spring to pick up annotations, let's look locations and definition of the JAX-RS 2.0 annotations.

The annotations can be found in the following files:
- `ApplicationConfig.java` (under the Service project)
  - This file contains the _root_ path definition for the project.
  - The root path definition is setup with the `ApplicationPath` annotation:
```java
@ApplicationPath("/v1/${webContext}/")
```
- `SampleResource.java` (under the API project)
  - This is the interface used to markup the sample resource
  - If you are familiar with the JAX-RS annotations, the mapping should be easy to understand


The sample resource is quite trivial, but again, it shows the pattern recommended by the Raptor Development Team. The implementation file can be found in class `SampleResourceImpl.java`, under the Service project.

Notice that the implementation file does not provide any JAX-RS mapping. Instead, the mapping is picked up by inheriting the interface we looked at in the discussion of the JAX-RS mapping.


As you develop your new service, you would want to remove the sample code from your project. Here is a simple checklist of all the things you should do to remove the test code from this project:

- [ ] Remove the interface file `SampleResource.java` (under the API project)
- [ ] Remove the implementation file  `SampleResourceImpl.java` (under the Service project)
- [ ] Define your own package structure (unless the package name set in the archetype is the correct one already. This is probably best performed by using the refactoring tool of your IDE. Make sure you change the package structure in all the submodules (api/implementation/functional test)
- [ ] Change the JAX-RS annotation for the root path in the application file (originally in `ApplicationConfig.java` class (under Service project). 
- [ ] If necessary, add extra packages to be scanned in the `ComponentScan` annotation, which is in `RaptorApplication.java` (under Service project).
- [ ] Remove (or customize) `SampleResourceFunctionalTest.java` class (under `FunctionalTests` project)
- [ ] Rewrite this README.md file to describe your project and help newcomers to your project navigate your project structure

# Recommended process for pushing new changes
1. Fork the base repository. Make sure develop branch is in sync with release branch.
2. Add your changes in develop branch and create Pull request with base repository's develop branch as target.
3. Get this PR approved and merged into develop branch.
4. Run CI Build for the component (CI builds- https://engineering.paypalcorp.com/confluence/display/SRE/CFBT+Development+Instances ), providing "develop" as branch parameter.
5. With successful CI build a manifest is created, update testenv with this manifest and run FT Build with testenv.
6. After successful FT Build, raise a pull request from develop branch of base repository to the release branch.
7. Get this PR approved and merged into release branch.
