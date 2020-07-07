This is an example of how to enable the Security Manager in Java. Since no security manager is enabled by default, and all security checks to protected resources and operations are disabled, enabling the security manager implies that you should:

Create a new SecurityManager Object.
Invoke the setSecurityManager(SecurityManager s) API method of the System, in order to enable the new security manager.
Invoke the setProperty(String key, String value) API method of the System, with parameters the name and the value of a system property (“java.home” and “123456” in this example). The method invocation will throw an AccessControlException, since the security manager is now enabled and the access to the system property is now not allowed.
Let’s take a look at the code snippet that follows:

```
package com.javacodegeeks.snippets.core;
 
import java.security.AccessControlException;
 
public class EnableSecurityManager {
  
  public static void main(String[] args) {
 
    /*
     No security manager is enabled by default. Thus all security checks 
     to protected resources and operations are disabled. In order to enable 
     security checks, the security manager must be enabled also
    */
 
    // Security manager is disabled, read/write access to "java.home" system property is allowed
    System.setProperty("java.home", "123456");
    System.out.println("java.home is : " + System.getProperty("java.home"));
 
    // Enable the security manager
    try {
 
  SecurityManager securityManager = new SecurityManager();
 
  System.setSecurityManager(securityManager);
    } catch (SecurityException se) {
 
  // SecurityManager already set
    }
 
    try {
 
  System.setProperty("java.home", "123456");
    } catch (AccessControlException ace) {
 
 System.out.println("Write access to the java.home system property is not allowed!");
    }
 
  }
}
```

Output:

```
java.home is : 123456
Write access to the java.home system property is not allowed!
```

Refs: https://examples.javacodegeeks.com/core-java/security/enable-the-security-manager-example/
