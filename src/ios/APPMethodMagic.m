/*
  Copyright 2013-2017 appPlant GmbH
  Licensed under Apache License, Version 2.0
*/

#import "APPMethodMagic.h"
#import <objc/runtime.h>
#import <objc/message.h>

/**
 * Swizzle class method specified by class and selector
 * through the provided method implementation.
 *
 * @param [ Class ] clazz The class containing the method.
 * @param [ SEL ] selector The selector of the method.
 * @param [ IMP ] newImpl The new implementation of the method.
 *
 * @return [ IMP ] The previous implementation of the method.
 */
IMP class_swizzleClassSelector(Class clazz, SEL selector, IMP newImpl)
{
    return class_swizzleSelector(object_getClass(clazz), selector, newImpl);
}

/**
 * Swizzle class method specified by class and selector
 * through the provided code block.
 *
 * @param [ Class ] clazz The class containing the method.
 * @param [ SEL ] selector The selector of the method.
 * @param [ id ] newImplBlock The new implementation of the method.
 *
 * @return [ IMP ] The previous implementation of the method.
 */
IMP class_swizzleClassSelectorWithBlock(Class clazz, SEL selector, id newImplBlock)
{
    IMP newImpl = imp_implementationWithBlock(newImplBlock);
    return class_swizzleClassSelector(clazz, selector, newImpl);
}

/**
 * Swizzle method specified by class and selector
 * through the provided code block.
 *
 * @param [ Class ] clazz The class containing the method.
 * @param [ SEL ] selector The selector of the method.
 * @param [ id ] newImplBlock The new implementation of the method.
 *
 * @return [ IMP ] The previous implementation of the method.
 */
IMP class_swizzleSelectorWithBlock(Class clazz, SEL selector, id newImplBlock)
{
    IMP newImpl = imp_implementationWithBlock(newImplBlock);
    return class_swizzleSelector(clazz, selector, newImpl);
}

/**
 * Swizzle method specified by class and selector
 * through the provided method implementation.
 *
 * @param [ Class ] clazz The class containing the method.
 * @param [ SEL ] selector The selector of the method.
 * @param [ IMP ] newImpl The new implementation of the method.
 *
 * @return [ IMP ] The previous implementation of the method.
 */
IMP class_swizzleSelector(Class clazz, SEL selector, IMP newImpl)
{
    @try {
        Method method = class_getInstanceMethod(clazz, selector);
        if (!method) return NULL;
        
        const char *types = method_getTypeEncoding(method);
        
        // Safe fallback for iOS 8+: Use method_exchangeImplementations if available
        if (class_addMethod(clazz, selector, newImpl, types)) {
            class_replaceMethod(clazz, selector, newImpl, types);
            return method_getImplementation(method);
        } else {
            Method origMethod = class_getInstanceMethod(clazz, selector);
            method_exchangeImplementations(origMethod, method);
            return method_getImplementation(origMethod);
        }
    } @catch (NSException *exception) {
        NSLog(@"BackgroundMode: Swizzling failed for %@ - %@", NSStringFromClass(clazz), NSStringFromSelector(selector));
        return NULL;
    }
}