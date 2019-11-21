# react-native-warper

## Getting started

`$ npm install react-native-warper --save`

### Mostly automatic installation

`$ react-native link react-native-warper`

### Manual installation


#### Android

1. Open up `android/app/src/main/java/[...]/MainApplication.java`
  - Add `import vn.focal.warper.WarperPackage;` to the imports at the top of the file
  - Add `new WarperPackage()` to the list returned by the `getPackages()` method
2. Append the following lines to `android/settings.gradle`:
  	```
  	include ':react-native-warper'
  	project(':react-native-warper').projectDir = new File(rootProject.projectDir, 	'../node_modules/react-native-warper/android')
  	```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
  	```
      compile project(':react-native-warper')
  	```


## Usage
```javascript
import Warper from 'react-native-warper';

// TODO: What to do with the module?
Warper;
```
