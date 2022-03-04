package com.example.testapp

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.meituan.robust.patch.annotaion.Modify

class MainActivity : AppCompatActivity() {

    @Modify
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}