/*
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.contacts.common.util;

/**
 * Encapsulates a threadsafe singleton pattern.
 *
 * This class is designed to be used as a public constant, living within a class that has a private constructor.
 * It defines a {@link #create(I)} method that will only ever be called once, upon the first call of {@link #get(I)}.
 * That method is responsible for creating the actual singleton instance, and that instance will be returned for all
 * future calls of {@link #get(I)}.
 *
 * Example:
 * <code>
 *     public class FooSingleton {
 *         public static final SingletonHolder&lt;FooSingleton, ParamObject&gt; HOLDER =
 *                 new SingletonHolder&lt;FooSingleton, ParamObject&gt;() {
 *                     @Override
 *                     protected FooSingleton create(ParamObject param) {
 *                         return new FooSingleton(param);
 *                     }
 *                 };
 *
 *         private FooSingleton(ParamObject param) {
 *
 *         }
 *     }
 *
 *     // somewhere else
 *     FooSingleton.HOLDER.get(params).doStuff();
 * </code>
 * @param <E> The type of the class to hold as a singleton.
 * @param <I> A parameter object to use during creation of the singleton object.
 */
public abstract class SingletonHolder<E, I> {
    E mInstance;
    final Object LOCK = new Object();

    public E get(I initializer) {
        if (null == mInstance) {
            synchronized (LOCK) {
                if (null == mInstance) {
                    mInstance = create(initializer);
                }
            }
        }

        return mInstance;
    }

    protected abstract E create(I initializer);

    /**
     * Specialized version of {@link SingletonHolder} which will keep a count of referring
     * objects, and clear the instance when all references have been removed.
     *
     * @param <E> The type of the class to hold as a singleton.
     * @param <I> A parameter object to use during creation of the singleton object.
     */
    public static abstract class RefCountedSingletonHolder<E, I> extends SingletonHolder<E, I> {
        private int mRefCount;

        public E get(I initializer) {
            synchronized (LOCK) {
                mRefCount++;
            }
            return super.get(initializer);
        }

        public void release() {
            synchronized (LOCK) {
                if(--mRefCount == 0) {
                    destroy(mInstance);
                    mInstance = null;
                }
            }
        }

        protected abstract void destroy(E instance);
    }
}
