## About PSP Net Party

PSP NetParty (PNP) is like XLink Kai and adhocparty (ps3).
Adhoc Tunneling software that enables PSP communication play via the Internet. 

![pspnetparty](https://user-images.githubusercontent.com/98584614/151692080-67e2408b-8970-4977-984f-6666bbda1f34.png)


As of now, it is usable but with some minor issues such as not being able to follow ssid's on linux.
There are also some translating mistakes where eclipse automatically made a new line when I pasted the english text, which messed up the ui. Will fix if there's any interest.

There is no server being hosted for this program.


**It is not necessary to open the port on the client side.**



- This software is free software (GPL).
- It is distributed after sufficient testing by developers and testers, but 
  The author does not take any responsibility for any damage caused by using this software. 
  Please use at your own risk.
- You are free to distribute and reprint.

## Official Site
https://github.com/montehunter/PSP-NetParty/wiki

## How to play

Install winpcap and then launch PlayClient.jar
Again, currently there is no master server, so you must host yourself.
The dll's included are for 64bit Windows. If you require 32bit then you can get them from https://github.com/montehunter/PSP-NetParty/releases/tag/VERSION_0.8

## How to build

tested on eclipse 3.6.2

Download the latest CompileTools.zip and merge with the github repository.

For ant build, prepare build.properties by yourself and 
Specify publish_directory as the directory to export the PSP NetParty jar


