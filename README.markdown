##Battery Monitor

###Introduce

_This is my first common Android app, as a newbie of Android and JAVA, I had
learn many thing from developing this app. Please contract me if you have
any suggestion._

This app focus on monitoring the battery level. After detecting the percentage
of battery level is changed, it will calculate the used rate in this period.
It also provide a chart to show two visual curve to show change of battery level
and battery used rate.

###Download Apk

If you only want to use this app, you can directly download the latest 
**release** version in the 
[Download List](https://github.com/newkedison/android-battery-monitor/downloads)

###Compile form source code

If you want to compile the source code and sign with your own key, you can
clone this project and compile it yourself.

Because I develop it with [VIM](http://www.vim.org) 
and [eclim](http://eclim.org) and compile it by command line, if you
also prefer command line, you can easily run this command to build a **signed
debug version**(make sure the `adb` and `ant` is in you PATH):

    ant clean debug

or build an **unsigned release version** by

    ant clean release

then use `jarsigner` and `zipalign` to sign and align. For more infomation 
about sign an apk, please refer to 
[this article](http://newkedison.tk/blog/how-to-make-android-app.html) in my 
blog(written in Chinese).

If you prefer to use eclipse IDE, you may be need to build a project. I haven't
try to use it, so I cannot give any suggestion.

NOTE: I had write a short script to make a release version in one command(see 
the [release](https://github.com/newkedison/android-battery-monitor/blob/master/release) 
file in the root folder), you can modify it to fit your need(because I had hard
code the file path and file name which cannot found in you system)
