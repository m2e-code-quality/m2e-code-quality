#!/bin/bash

mkdir -p letsencrypt
pushd letsencrypt

echo "
Update the file "challenge" in the root dir of https://github.com/m2e-code-quality/m2e-code-quality.github.io:
 https://github.com/m2e-code-quality/m2e-code-quality.github.io/edit/master/challenge

---
layout: none
permalink: .well-known/acme-challenge/<TOKEN>
---
<Complete-TOKEN>

"

certbot certonly -d m2e-code-quality.github.io --manual --preferred-challenges http \
    --config-dir $(pwd) --work-dir $(pwd) --logs-dir $(pwd)

popd

echo -n "Enter the passphrase for encrypting the keystore (code-signing.p12): "
read -s passphrase
echo

PKCS12_PASSPHRASE="${passphrase}" \
    openssl pkcs12 -export -inkey letsencrypt/live/m2e-code-quality.github.io/privkey.pem \
    -in letsencrypt/live/m2e-code-quality.github.io/fullchain.pem  \
    -name code-signing \
    -password env:PKCS12_PASSPHRASE \
    -out code-signing.p12
