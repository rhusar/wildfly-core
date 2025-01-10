# Building SNAPSHOT or Unreleased Version of WildFly Core EAP

When building an unreleased version of JBoss EAP, it is necessary to be connected to the Red Hat VPN in order to have
access to the internal JBoss EAP Product Repository that contains unreleased dependencies. It is also needed to add the
Red Hat root certificate as trusted certificate on your JDK keystore, otherwise you won't ve able to access the
JBoss EAP Product Repository via HTTPS protocol.

### How to install the Red Hat root certificate on your JDK keystore
Follow these instructions to install the Red Hat root certificate as trusted certificate on your JDK keystore:
1. Download the Red Hat root certificate:
```
wget https://password.corp.redhat.com/RH-IT-Root-CA.crt
```
2. Install it on your JDK keystore:
* For JDK 11:
   ```
   keytool -import -alias internal.redhat.com -keystore <JDK_11_Path>/lib/security/cacerts -file RH-IT-Root-CA.crt -storepass changeit
  ```
If you are using system provided JDK(rpm installed), like: java-11-openjdk-devel, using the following command also works:
```
curl https://password.corp.redhat.com/RH-IT-Root-CA.crt -o /etc/pki/ca-trust/source/anchors/RH-IT-Root-CA.crt
update-ca-trust
```