
# API Gatekeeper email service

This service is a part of Gatekeeper.
It facilitates sending emails to API producers via HMRC's digital contact service.

## Running
To start the application, execute
~~~
sbt run
~~~
then go to http://localhost:9000/gatekeeper-compose-email-frontend/ in your browser

## Running tests
To run the tests, execute
~~~
./run_all_tests.sh
~~~

Note that the tests rely on `chromedriver` being on your PATH, which can be downloaded from [here]("https://chromedriver.chromium.org/downloads").

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").