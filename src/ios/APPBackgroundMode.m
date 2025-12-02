/*
  Copyright 2013-2017 appPlant GmbH
  Licensed under Apache License, Version 2.0
*/

#import "APPMethodMagic.h"
#import "APPBackgroundMode.h"
#import <Cordova/CDVAvailability.h>

@implementation APPBackgroundMode

#pragma mark -
#pragma mark Constants

NSString* const kAPPBackgroundJsNamespace = @"cordova.plugins.backgroundMode";
NSString* const kAPPBackgroundEventActivate = @"activate";
NSString* const kAPPBackgroundEventDeactivate = @"deactivate";

#pragma mark -
#pragma mark Life Cycle

/**
 * Called by runtime once the Class has been loaded.
 * Exchange method implementations to hook into their execution.
 */
+ (void) load
{
    [self swizzleWKWebViewEngine];
}

/**
 * Initialize the plugin.
 */
- (void) pluginInitialize
{
    enabled = NO;
    [self configureAudioPlayer];
    [self configureAudioSession];
    [self observeLifeCycle];
}

/**
 * Register the listener for pause and resume events.
 */
- (void) observeLifeCycle
{
    NSNotificationCenter* listener = [NSNotificationCenter defaultCenter];

    [listener addObserver:self
                     selector:@selector(keepAwake)
                         name:UIApplicationDidEnterBackgroundNotification
                       object:nil];

    [listener addObserver:self
                     selector:@selector(stopKeepingAwake)
                         name:UIApplicationWillEnterForegroundNotification
                       object:nil];

    [listener addObserver:self
                     selector:@selector(handleAudioSessionInterruption:)
                         name:AVAudioSessionInterruptionNotification
                       object:nil];
}

#pragma mark -
#pragma mark Interface

/**
 * Enable the mode to stay awake
 * when switching to background for the next time.
 */
- (void) enable:(CDVInvokedUrlCommand*)command
{
    if (enabled) return;

    enabled = YES;
    [self execCallback:command];
}

/**
 * Disable the background mode
 * and stop being active in background.
 */
- (void) disable:(CDVInvokedUrlCommand*)command
{
    if (!enabled) return;

    enabled = NO;
    [self stopKeepingAwake];
    [self execCallback:command];
}

#pragma mark -
#pragma mark Core

/**
 * Keep the app awake.
 */
- (void) keepAwake
{
    if (!enabled) return;

    [audioPlayer play];
    [self fireEvent:kAPPBackgroundEventActivate];
}

/**
 * Let the app going to sleep.
 */
- (void) stopKeepingAwake
{
    if (TARGET_IPHONE_SIMULATOR) {
        NSLog(@"BackgroundMode: On simulator apps never pause in background!");
    }

    if (audioPlayer.isPlaying) {
        [self fireEvent:kAPPBackgroundEventDeactivate];
    }

    [audioPlayer pause];
}

/**
 * Configure the audio player.
 */
- (void) configureAudioPlayer
{
    NSString* path = [[NSBundle mainBundle] pathForResource:@"appbeep" ofType:@"wav"];

    NSURL* url = [NSURL fileURLWithPath:path];

    // FIXED: Fallback to silent data if file missing (iOS 8+ compatible)
    if (![[NSFileManager defaultManager] fileExistsAtPath:path]) {
        url = [NSURL URLWithString:@"data:audio/wav;base64,UklGRnoGAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQoGAACBhYqFbF1fdJivrJBhNjVgodDbq2EcBj+a2/LDciUFLIHoQjNo0DQEAA"];
    }

    NSError* error = nil;
    audioPlayer = [[AVAudioPlayer alloc] initWithContentsOfURL:url error:&error];
    if (error) {
        NSLog(@"BackgroundMode: Audio player init failed: %@", error);
        return;
    }

    audioPlayer.volume = 0;
    audioPlayer.numberOfLoops = -1;
}

/**
 * Configure the audio session.
 */
- (void) configureAudioSession
{
    AVAudioSession* session = [AVAudioSession sharedInstance];

    // FIXED: iOS 16+ requires mixing for silent background audio
    NSError* error = nil;
    [session setCategory:AVAudioSessionCategoryPlayback
                   mode:AVAudioSessionModeDefault
                options:AVAudioSessionCategoryOptionMixWithOthers | AVAudioSessionCategoryOptionDuckOthers
                  error:&error];
    if (error) {
        NSLog(@"BackgroundMode: Audio session category failed: %@", error);
    }

    [session setActive:NO error:&error];
    if (error) {
        NSLog(@"BackgroundMode: Deactivating audio session failed: %@", error);
    }

    [session setActive:YES error:&error];
    if (error) {
        NSLog(@"BackgroundMode: Activating audio session failed: %@", error);
    }
}

#pragma mark -
#pragma mark Helper

/**
 * Simply invokes the callback without any parameter.
 */
- (void) execCallback:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
}

/**
 * Restart playing sound when interrupted by phone calls.
 */
- (void) handleAudioSessionInterruption:(NSNotification*)notification
{
    [self fireEvent:kAPPBackgroundEventDeactivate];
    [self keepAwake];
}

/**
 * Find out if the app runs inside the webkit powered webview.
 */
+ (BOOL) isRunningWebKit
{
    // FIXED: Replace deprecated IsAtLeastiOSVersion with @available
    if (@available(iOS 8.0, *)) {
        return NSClassFromString(@"CDVWKWebViewEngine") != nil;
    }
    return NO;
}

/**
 * Method to fire an event with some parameters in the browser.
 */
- (void) fireEvent:(NSString*)event
{
    NSString* active = [event isEqualToString:kAPPBackgroundEventActivate] ? @"true" : @"false";

    NSString* flag = [NSString stringWithFormat:@"%@._isActive=%@;", kAPPBackgroundJsNamespace, active];

    NSString* depFn = [NSString stringWithFormat:@"%@.on('%@');", kAPPBackgroundJsNamespace, event];

    NSString* fn = [NSString stringWithFormat:@"%@.fireEvent('%@');", kAPPBackgroundJsNamespace, event];

    NSString* js = [NSString stringWithFormat:@"%@%@%@", flag, depFn, fn];

    [self.commandDelegate evalJs:js];
}

#pragma mark -
#pragma mark Swizzling

/**
 * Method to swizzle.
 */
+ (NSString*) wkProperty
{
    // FIXED: Guard for iOS 14+ (alwaysRunsAtForegroundPriority deprecated in iOS 18)
    if (@available(iOS 14.0, *)) {
        NSString* str = @"YWx3YXlzUnVuc0F0Rm9yZWdyb3VuZFByaW9yaXR5";
        NSData* data = [[NSData alloc] initWithBase64EncodedString:str options:0];
        return [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    }
    return nil;
}

/**
 * Swizzle some implementations of CDVWKWebViewEngine.
 */
+ (void) swizzleWKWebViewEngine
{
    if (![self isRunningWebKit]) return;

    Class wkWebViewEngineCls = NSClassFromString(@"CDVWKWebViewEngine");
    if (!wkWebViewEngineCls) return;

    SEL selector = NSSelectorFromString(@"createConfigurationFromSettings:");

    // FIXED: Safe swizzling with try-catch for Cordova iOS 8+ stability
    @try {
        SwizzleSelectorWithBlock_Begin(wkWebViewEngineCls, selector)
        ^(CDVPlugin *self, NSDictionary *settings) {
            id obj = ((id (*)(id, SEL, NSDictionary*))_imp)(self, _cmd, settings);

            NSString* prop = [self wkProperty];
            if (prop) {
                [obj setValue:[NSNumber numberWithBool:YES] forKey:prop];
            }

            [obj setValue:[NSNumber numberWithBool:NO] forKey:@"requiresUserActionForMediaPlayback"];

            return obj;
        }
        SwizzleSelectorWithBlock_End;
    } @catch (NSException *exception) {
        NSLog(@"BackgroundMode: WKWebView swizzling failed: %@", exception);
    }
}

@end