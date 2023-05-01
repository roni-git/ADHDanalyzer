package com.example.adhd_analyzer;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.room.Room;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

public class SensorsRecordsService extends Service implements SensorEventListener, LocationListener {

    private SensorManager sensorManager;
    private LocationManager locationManager;
    private BufferedWriter csvWriter;
    private float[] accelerometerData;
    private float[] gyroscopeData;
    private float[] magnetometerData;
    private Date startTime;
    private SensorsDB db;
    private SensorLogDao logDao;

    private File csvFile;

    public SensorsRecordsService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startTime = new Date();

        // Initialize sensor manager
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometerData = new float[3];
        gyroscopeData = new float[3];
        magnetometerData = new float[3];

        // Register sensors
        Sensor accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this,magnetometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        // Initialize location manager
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Register location updates
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);

        db = Room.databaseBuilder(getApplicationContext(),SensorsDB.class,"sensorsDB").allowMainThreadQueries().build();
        logDao = db.sensorLogDao();
        // Create CSV file
        csvFile = new File(getExternalFilesDir(null), "sensor_data.csv");
        try {
            csvWriter = new BufferedWriter(new FileWriter(csvFile));
            csvWriter.write("Time,Accelerometer X,Accelerometer Y,Accelerometer Z,Gyroscope X,Gyroscope Y,Gyroscope Z,Latitude,Longitude,Altitude\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Date now = new Date();
        if (now.getTime()-startTime.getTime()>1000*60*60*24){
            return;
        }
        // Record sensor data
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelerometerData = event.values.clone();
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroscopeData = event.values.clone();
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magnetometerData = event.values.clone();
        }

        // Check if we have GPS data
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location != null) {
            // Format sensor data as CSV
            long timestamp = System.currentTimeMillis();
            float accelerometerX = accelerometerData[0];
            float accelerometerY = accelerometerData[1];
            float accelerometerZ = accelerometerData[2];
            float gyroscopeX = gyroscopeData[0];
            float gyroscopeY = gyroscopeData[1];
            float gyroscopeZ = gyroscopeData[2];
            float magnetometerX = magnetometerData[0];
            float magnetometerY = magnetometerData[1];
            float magnetometerZ = magnetometerData[2];
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            double altitude = location.getAltitude();
            SensorLog log = new SensorLog(timestamp ,accelerometerX ,accelerometerY , accelerometerZ,gyroscopeX,gyroscopeY,gyroscopeZ,magnetometerX,magnetometerY,magnetometerZ,latitude,longitude,altitude);
            logDao.insert(log);

            String csvLine = timestamp + "," + accelerometerX + "," + accelerometerY + "," + accelerometerZ + "," + gyroscopeX + "," + gyroscopeY + "," + gyroscopeZ + "," + latitude + "," + longitude + "," + altitude + "\n";

            // Write sensor data to CSV file
            try {
                csvWriter.write(csvLine);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    public void onDestroy() {
        super.onDestroy();

        // Stop recording
        sensorManager.unregisterListener(this);
        locationManager.removeUpdates(this);
        try {
            csvWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {

    }


    public String getCsvFilePath() {
        // Return the file path
        return csvFile.getAbsolutePath();
    }
}