This is an old project I worked on in Year 12 (NCEA Level 3) in high school (2019). I continued working on it for a year afterwards, during my first year of university. Then I left to serve a volunteer mission for 2 years, and so I was not able to work on it any more and haven't had the opportunity to work on it since.

The project can be summarised as a VR headset & controller system, designed to be low cost but functional. This repository contains the android app portion, which forms the backend of the system. It would communicate via Bluetooth to read sensor data from the controllers (finger tracking and orientation) and use the phone's camera to calculate the position of the controllers in 3d space. It serves this data over the network to an app/game.
See also bertkdowns/extendvr-web, which is an example frontend that makes use of this data, and bertkdowns/extendvr-arduino, which contains the arduino code.

Below is one of my writeups of the project during High School. I achieved an NCEA Technology Scholarship for this work. I also entered this project in the Skills Bright Sparks competition, and won First Place in the Senior category.
https://www.nzherald.co.nz/te-awamutu-courier/news/te-awamutu-college-student-bert-downs-wins-in-skills-bright-sparks-competition/QRCVYIOI3OQI2ENDSLSSNFRUNM/

## ExtendVR writeup (dated 27 September 2019)
I’ve managed to achieve both 2-axis positional and 3-axis rotational controller tracking, on top of
finger tracking (which is usually a luxury), in a VR environment. It does this by combining the power
of the arduino and some custom electronics hardware with my own camera tracking algorithm/app
designed specifically for this, and is brought all together by the web browser which will eventually
allow you to use many WebVR apps, even ones that require room-scale VR, without an expensive Vive,
Rift, or Quest.

It is good because:

• it allows VR, which has potential in everything from pain relief to learning, to be accessed by
more people in a more useful way

• It makes use of your existing hardware – you don’t have to buy a expensive headset, just a
cheap google cardboard one and the controllers

• It works with WebVR, where there are already apps out there you can use, and I will be making
a chrome extension to allow them to work with Extend VR, and allows for others to easily make
apps as well.

• It gets the best of all worlds, with the potential of fast tracking performance due to native
android apps, and WebVR

Design choices:

• I chose to use Web VR because of the ease with which you can make applications, and because
it allows me to make existing WebVR apps work on Extend VR, without me having to make the
library of apps myself – there are great apps out there already, such as A-Painter, sound-boxing,
and more

• By separating the positional tracking of the controller out to a native app, I have the full android
camera2 api to play around with, allowing me greater functionality than if I had just used the
web camera api, which doesn’t allow me to lock exposure among other things. It also allows me
more flexibility when it comes to performance, with options such as threading more readily
available.

• My ingenious finger tracking technique provides a stand out from other low end VR options;
the only real consumer VR solution with finger tracking is the insanely expensive Valve Index.
It also increases immersion and allows users to more intuitively interact with the VR world.
Also, because it does not use more complex capacitive touch hardware and software, it
decreases the cost to produce the controller without drastically decreasing the usability of the
controllers, and should be more reliable (capacitive touch is very sensitive to even the slightest
change in temperature or humidity, and has to be constantly recalibrated).

Next Steps:

• Improve the performance of the position tracking, and make it track forwards-backwards better
(instead of just side-to-side and up-down: forwards-backwards relies on the size of the tracking
light to the camera, which is harder to get an accurate reading for, so I have disabled it for now).
This was literally the first Java android app I ever made, and the first time I’ve really used Java,
so I still have lots of learning to go on that front, though I am proud that I managed to figure it
out.

• Add a Lithium battery to the controller (I basically just haven’t had time to do this yet) and
make another controller, so I can have one for each hand like you’re supposed to. I will have to
modify my code some more to support this.

• Make a Chrome Extension to automatically insert the desired scripts into a WebVR page so that
it is compatible with ExtendVR.There are no real other VR options out there that even have positional tracking controllers that use
phone vr, and the people that make this kind of stuff are big companies, not a single person working in
their spare time, so it’s pretty amazing I have even got this far!

## Brightsparks Writeup: (November 2019)

Extend VR

Extend VR is a new, alternative Virtual Reality system, kinda like the HTC Vive or Oculus Rift. The difference is that rather than requiring you to purchase an expensive headset and computer, it runs on your phone and uses your phone to power the headset. 

I used Google Cardboard as a starting point for this project, and expanded it and added a controller. The controller is tracked via your phone’s camera, which looks for the colored ball on the top of the controller. The controller also includes finger tracking to make using VR more immersive and natural.

The controller uses a MPU-6050 to get rotation data, and sends it to the phone via bluetooth. I have also created a custom array of moisture sensors, that works using a similar method to how a lot of 3 by 4 matrix keypads work. This means I don’t need too many pins to interface with a lot of moisture sensors. 

Currently I am using the A-Frame WebVR framework to power my example VR application. I will be able to make Extend VR compatible with existing A-Frame VR applications, meaning that I do not have to program everything specifically for Extend VR, as it will support some existing applications.

A dedicated android app controls the camera tracking and processes the image to determine controller locations. The A-Frame web app then interfaces with the android app to get the processed information, and moves the in-game hand model to that location. The web app also interfaces directly with the controller to obtain rotation and hand tracking information, which is used to rotate the hand and the fingers.


### Designing the controller

I started off with a glove controller design, but that didn’t work well as the finger tracking was analogue and inaccurate. I tried a few different methods, but none of them worked well. So I decided to make a controller more like the Valve Index controllers, with touch sensors to detect finger positioning.  However, I used the resistive “moisture sensor” touch sensors rather than capacitive touch sensors, as they are easy to calibrate.

### Designing the position tracking

I thought using the phone’s camera would be a good way to implement position tracking as it means no special hardware has to be purchased, and it has been proven to work:PSVR uses it. So I put together a little program in javascript that tracks a colored ball using the camera. But colored balls wouldn’t cut it, because shadows. So I made a glowing ping pong ball! Then Javascript wouldn’t cut it, because the web camera api gives you no control over the image, and for glowing ping pong balls to show up right the exposure has to be low. I stumbled around in Android studio until I figured out how to use the camera2 api enough to be able to lock exposure, and then ported my tracking algorithm from javascript to java. 
