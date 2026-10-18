# FTPS loopback fixture

`localhost.p12` is a public, test-only self-signed key pair, password `fixture`.
Its only DNS subject alternative name is `localhost`; connecting as `127.0.0.1`
must fail hostname verification even when the fixture certificate is trusted.
The certificate is valid from 2020-01-01 for 36,500 days so ordinary clock changes
do not turn these protocol tests into certificate-expiry tests.

Tests load this keystore into a private `SSLContext`. Production clients retain
the platform default trust manager and never load this resource. No global trust
store or hostname verifier is changed. All test sockets bind to loopback.

Regenerate with a JDK `keytool` after removing only the old fixture file:

```text
keytool -genkeypair -alias localhost -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=localhost,OU=Test Only,O=Atomic File Manager" -ext "SAN=dns:localhost" -startdate "2020/01/01 00:00:00" -validity 36500 -storetype PKCS12 -keystore localhost.p12 -storepass fixture -keypass fixture -noprompt
```

These tests cover explicit FTPS, protected passive data channels, handshake
ordering, certificate trust/host rejection, failure replies and plain FTP.
They do not establish compatibility with every FTP server, TLS session-reuse
policy, EPSV/IPv6, or remote storage atomicity.
