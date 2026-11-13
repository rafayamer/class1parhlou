# Prototype signing key

`prototype.jks` (password `parhlou-prototype`, alias `parhlou`) signs **prototype builds only**, so that an
automatically built APK can install over the previous one. It is committed on purpose and is public.

**Never use it for the Play Store.** A store release must be signed with a private key that is never committed.
