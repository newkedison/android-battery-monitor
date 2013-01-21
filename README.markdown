##Battery Monitor

###Introduce

_This is my first common Android app, as a newbie of Android and JAVA, I had
learn many thing from developing this app. Please contract me if you have
any suggestion._

This app focus on monitoring the battery level. After detecting the percentage
of battery level being changed, it will calculate the used rate in this period.
It also provide a chart to show two visual curve for change of battery level
and battery used rate.

###Download Apk

**NOTE**
Unfortunately, github
[deprecated download page](https://github.com/blog/1302-goodbye-uploads)
recently. So I will upload the **release** apk to another place in the future.


~~If you only want to use this app, you can directly download the latest 
**release** version in the 
[Download List](https://github.com/newkedison/android-battery-monitor/downloads)~~

###Compile form source code

If you want to compile the source code and sign with your own key, you can
clone this project and compile it yourself, just bear in mind a few things:

* This project is set up to be built by Ant, not by Eclipse. 
If you wish to use the code with Eclipse, you will need to create a suitable 
Android Eclipse project and import the code and other assets.

* You should delete build.xml from the project, then run 
`android update project -p .` in the project folder, 
so the build file will be updated for your Android SDK version.

* In order to reuse some code, they were being extractted into another
repository
[newkedison/android-library](https://github.com/newkedison/android-library).
You must clone that repository too, and put these two repositories
in the same folder.

You can  build a **signed debug version** by(make sure
that both `adb` and `ant` can be find in your $PATH):

    ant clean debug

or build an **unsigned release version** by

    ant clean release

then use `jarsigner` and `zipalign` to sign and align. For more infomation 
about sign an apk, please refer to 
[this article](http://newkedison.tk/blog/how-to-make-android-app.html) in my 
blog( **written in Chinese** ).

**NOTE:** I had write a short script to make a release version in one command
(see the
[release](https://github.com/newkedison/android-battery-monitor/blob/master/release) 
file in the root folder), you can modify it to fit your need(because I had hard
code the file path and file name which cannot found in you system)
