package com.neiderlizcano.gestorpedidos.ionic;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

// Se ejecuta en un dispositivo o emulador Android
@RunWith(AndroidJUnit4.class)
public class AppContextInstrumentedTest {

    @Test
    public void usesAppPackageName() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // El identificador debe coincidir con appId de capacitor.config.ts
        assertEquals("com.neiderlizcano.gestorpedidos.ionic", appContext.getPackageName());
    }
}
