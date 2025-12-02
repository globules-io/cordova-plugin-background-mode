/*
  Copyright 2013-2017 appPlant GmbH
  Licensed under Apache License, Version 2.0
*/

#import <objc/runtime.h>
#import <objc/message.h>

#define SwizzleSelector(clazz, selector, newImpl, oldImpl) \
(*oldImpl) = (__typeof((*oldImpl)))class_swizzleSelector((clazz), (selector), (IMP)(newImpl))

#define SwizzleClassSelector(clazz, selector, newImpl, oldImpl) \
(*oldImpl) = (__typeof((*oldImpl)))class_swizzleClassSelector((clazz), (selector), (IMP)(newImpl))

#define SwizzleSelectorWithBlock_Begin(clazz, selector) { \
SEL _cmd = selector; \
__block IMP _imp = class_swizzleSelectorWithBlock((clazz), (selector),
#define SwizzleSelectorWithBlock_End );}

#define SwizzleClassSelectorWithBlock_Begin(clazz, selector) { \
SEL _cmd = selector; \
__block IMP _imp = class_swizzleClassSelectorWithBlock((clazz), (selector),
#define SwizzleClassSelectorWithBlock_End );}

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
IMP class_swizzleClassSelector(Class clazz, SEL selector, IMP newImpl);

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
IMP class_swizzleClassSelectorWithBlock(Class clazz, SEL selector, id newImplBlock);

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
IMP class_swizzleSelectorWithBlock(Class clazz, SEL selector, id newImplBlock);

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
IMP class_swizzleSelector(Class clazz, SEL selector, IMP newImpl);