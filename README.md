# sherpa-servlet

Java Sherpa library exporting Java functions as a Java servlet.

Sherpa is a mechanism for simple web api's for web apps [1].

Use sherpa-servlet through maven central [2].

To see how you could use this library, we've created created an example [3].

- [1] https://www.ueber.net/who/mjl/sherpa/
- [2] https://google.com/search?q=nl.irias/sherpa-servlet%20maven
- [3] https://github.com/irias/sherpa-servlet-example

# Logging

Sherpa-servlet logs under name "nl.irias.sherpa", with the following messages logged at each log level:

- SEVERE: of sherpa functions that raise an exception other than SherpaUserException. or when your exception formatter throws an error.
- FINE: the raising of SherpaUserException by handlers.
- FINER: all sherpa calls with their parameters (except when they were annotated to have sensitive parameters).
- FINEST: full SherpaUserException raised by handlers.

# License

Created by Irias and released under an MIT-license, see LICENSE.md.
