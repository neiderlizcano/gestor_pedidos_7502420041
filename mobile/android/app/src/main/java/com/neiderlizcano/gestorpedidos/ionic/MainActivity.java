package com.neiderlizcano.gestorpedidos.ionic;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.neiderlizcano.gestorpedidos.ionic.securestorage.SecureSessionStoragePlugin;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Los plugins locales se registran antes de crear el puente
        registerPlugin(SecureSessionStoragePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
