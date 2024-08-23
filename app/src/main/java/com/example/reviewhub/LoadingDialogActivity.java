package com.example.reviewhub;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import javax.annotation.Nonnull;

public class LoadingDialogActivity extends Dialog{

    public LoadingDialogActivity(@Nonnull Context context){
        super(context);

        WindowManager.LayoutParams parems = getWindow().getAttributes();
        parems.gravity = Gravity.CENTER;
        getWindow().setAttributes(parems);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        setTitle(null);
        setCancelable(false);
        setOnCancelListener(null);
        View view = LayoutInflater.from(context).inflate(R.layout.activity_splash_screen, null);
        setContentView(view);

    }
}
