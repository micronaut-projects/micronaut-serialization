/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;

/**
 * Loads {@link UnloadableKeysProvider} in a disposable class loader and registers it with {@link KeysSupport}.
 */
public final class DisposableKeysProviderLoader extends ClassLoader {

    private static final String PROVIDER = "io.micronaut.serde.UnloadableKeysProvider";

    private DisposableKeysProviderLoader(ClassLoader parent) {
        super(parent);
    }

    /**
     * Registers a provider from a new disposable class loader and uses it once.
     *
     * @param register Whether to register the provider with {@link KeysSupport}
     * @return A weak reference to the disposable class loader
     * @throws Exception if the provider cannot be loaded
     */
    public static WeakReference<ClassLoader> registerProvider(boolean register) throws Exception {
        DisposableKeysProviderLoader loader = new DisposableKeysProviderLoader(DisposableKeysProviderLoader.class.getClassLoader());
        KeysProvider provider = (KeysProvider) loader.loadClass(PROVIDER).getDeclaredConstructor().newInstance();
        if (provider.getClass().getClassLoader() != loader) {
            throw new IllegalStateException("Provider was not loaded by the disposable class loader");
        }
        if (register) {
            int index = KeysSupport.indexOf(provider);
            Object[] contribution = KeysSupport.get(Keys.create("foo"), index);
            if (!"foo".equals(((String[]) contribution[0])[0])) {
                throw new IllegalStateException("Unexpected contribution");
            }
        }
        return new WeakReference<>(loader);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!PROVIDER.equals(name)) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    byte[] bytes = in.readAllBytes();
                    type = defineClass(name, bytes, 0, bytes.length);
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
            return type;
        }
    }
}
