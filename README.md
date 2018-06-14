# ToDo Item App

This is a simple implementation of a todo item app in Play. 

Note that this requires postgres running on port 5432, using the username postgres 
and without a password using the default properties.

These properties can be configured in application.conf or application.test.conf.
When running in test mode, it will automatically bring up an embedded instance for testing. 

By copying the properties from the test file you can run it with the embedded instance if you don't have postgres installed locally.   
# Testing
    sbt test
