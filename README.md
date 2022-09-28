# Beskedfordeler_kodeeksempler

The example from
<https://digitaliseringskataloget.dk/files/integration-files/030820221448/Kom%20godt%20i%20gang%20-%20beskedfordeler.pdf>
(found via “Fælleskommunal Beskedfordeler“ on
<https://digitaliseringskataloget.dk/kom-godt-i-gang-vejledninger>).

## macOS

Updating the code:

```sh
curl --location https://docs.kombit.dk/integration/sf1461/1.0/pakke > sf1461.zip
unzip sf1461.zip 'Beskedfordeler version 5.10.zip'
unzip 'Beskedfordeler version 5.10.zip' 'Beskedfordeler version 5.10/Beskedfordeler_kodeeksempler/*'
brew install openjdk@8
https://gradle.org/releases/#release-2-a

# https://gradle.org/install/#manually
curl --location https://services.gradle.org/distributions/gradle-4.6-bin.zip > gradle-4.6-bin.zip
unzip gradle-4.6-bin.zip

# Add gradle and jdk8 to PATH (cf. brew info openjdk@8)
export PATH="/usr/local/opt/openjdk@8/bin:$PWD/gradle-4.6/bin:$PATH"

brew install dos2unix
cd 'Beskedfordeler version 5.10/Beskedfordeler_kodeeksempler/'
dos2unix ./gradlew
```

Compiling and running

```sh
cd 'Beskedfordeler version 5.10/Beskedfordeler_kodeeksempler/'
sh ./gradlew afhentbesked --console=plain
```
