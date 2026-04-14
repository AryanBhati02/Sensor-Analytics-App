package com.mad.sensorapp.ui.charts;

import android.content.Context;
import android.graphics.Color;
import android.hardware.*;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import com.github.mikephil.charting.charts.*;
import com.github.mikephil.charting.components.*;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.interfaces.datasets.IRadarDataSet;
import com.google.android.material.tabs.TabLayout;
import com.mad.sensorapp.R;
import com.mad.sensorapp.utils.FFTAnalyzer;
import com.mad.sensorapp.views.*;
import java.util.*;

public class ChartsFragment extends Fragment implements SensorEventListener {
    private static final int MAX_DATA_POINTS = 150;
    private SensorManager sensorManager;
    private Sensor accel, light, proximity;
    private TabLayout   tabLayout;
    private WaveformView waveformView;
    private LineChart    lineChart;
    private LineChart    lightChart;
    private com.mad.sensorapp.views.GaugeView lightGauge;
    private HeatmapView  heatmapView;
    private RadarChart   radarChart;
    private BarChart     fftChart;
    private TextView     tvFftInfo, tvLightLux;
    private View panelWaveform, panelLine, panelLight, panelHeatmap, panelRadar, panelFft;
    private LineData accelLineData, lightLineData;
    private RadarData radarData;
    private float xIndex=0f, lastAccelMag=0f, lastLight=0f, lastProx=0f;
    private int currentTab=0, frameCount=0;
    private final float[] fftSamples = new float[256];
    private int fftWriteHead=0;
    private static final float SAMPLE_RATE_HZ = 20f;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_charts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle s) {
        super.onViewCreated(root, s);
        tabLayout=root.findViewById(R.id.chartTabLayout);
        waveformView=root.findViewById(R.id.waveformView);
        lineChart=root.findViewById(R.id.lineChart);
        lightChart=root.findViewById(R.id.lightChart);
        heatmapView=root.findViewById(R.id.heatmapView);
        radarChart=root.findViewById(R.id.radarChart);
        fftChart=root.findViewById(R.id.fftChart);
        tvFftInfo=root.findViewById(R.id.tvFftInfo);
        tvLightLux=root.findViewById(R.id.tvLightLux);
        lightGauge=root.findViewById(R.id.lightGauge);
        panelWaveform=root.findViewById(R.id.panelWaveform);
        panelLine=root.findViewById(R.id.panelLine);
        panelLight=root.findViewById(R.id.panelLight);
        panelHeatmap=root.findViewById(R.id.panelHeatmap);
        panelRadar=root.findViewById(R.id.panelRadar);
        panelFft=root.findViewById(R.id.panelFft);
        sensorManager=(SensorManager)requireContext().getSystemService(Context.SENSOR_SERVICE);
        accel=sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        light=sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        proximity=sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        setupLineChart(); setupLightChart(); setupRadarChart(); setupFftChart();
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener(){
            @Override public void onTabSelected(TabLayout.Tab t){currentTab=t.getPosition();switchPanel(currentTab);}
            @Override public void onTabUnselected(TabLayout.Tab t){}
            @Override public void onTabReselected(TabLayout.Tab t){}
        });
        switchPanel(0);
    }

    private void switchPanel(int idx){
        View[] p={panelWaveform,panelLine,panelLight,panelHeatmap,panelRadar,panelFft};
        for(int i=0;i<p.length;i++){
            if(p[i]==null)continue;
            if(i==idx){p[i].setVisibility(View.VISIBLE);p[i].setAlpha(0f);p[i].animate().alpha(1f).setDuration(200).setInterpolator(new FastOutSlowInInterpolator()).start();}
            else p[i].setVisibility(View.GONE);
        }
        if(tvFftInfo!=null)tvFftInfo.setVisibility(idx==5?View.VISIBLE:View.GONE);
    }

    private void setupLineChart(){
        styleChart(lineChart);
        accelLineData=new LineData(Arrays.asList((ILineDataSet)createDS("X",0xFF00E5FF),(ILineDataSet)createDS("Y",0xFF00E676),(ILineDataSet)createDS("Z",0xFFFFB300)));
        lineChart.setData(accelLineData);
    }
    private void setupLightChart(){
        if(lightGauge!=null){
            lightGauge.setRange(0f,1000f);
            lightGauge.setUnit("lx");
            lightGauge.setAccentColor(0xFFFFB300);
        }
        styleChart(lightChart);
        LineDataSet ds=createDS("Lux",0xFFFFB300);
        ds.setFillAlpha(60);ds.setDrawFilled(true);ds.setFillColor(0xFFFFB300);
        lightLineData=new LineData(ds);lightChart.setData(lightLineData);
    }
    private void setupRadarChart(){
        radarChart.setBackgroundColor(Color.TRANSPARENT);radarChart.getDescription().setEnabled(false);
        radarChart.setWebLineWidth(1.5f);radarChart.setWebColor(0xFF1E2A3A);radarChart.setWebLineWidthInner(1f);radarChart.setWebColorInner(0xFF1E2A3A);radarChart.setWebAlpha(120);
        radarChart.getYAxis().setTextColor(0xFF4A5568);radarChart.getYAxis().setDrawLabels(false);
        radarChart.getXAxis().setTextColor(0xFFEAEEF5);radarChart.getXAxis().setTextSize(13f);
        radarChart.getXAxis().setValueFormatter(new ValueFormatter(){
            final String[]labels={"Accel","Light","Prox"};
            @Override public String getFormattedValue(float v){int i=(int)v%labels.length;return i>=0?labels[i]:"";}
        });
        RadarDataSet ds=new RadarDataSet(new ArrayList<>(),"Sensors");
        ds.setColor(0xFF00E5FF);ds.setFillColor(0x4400E5FF);ds.setDrawFilled(true);ds.setFillAlpha(80);ds.setLineWidth(2f);
        radarData=new RadarData(Collections.singletonList((IRadarDataSet)ds));
        radarData.setValueTextColor(0xFFEAEEF5);radarChart.setData(radarData);
    }
    private void setupFftChart(){
        if(fftChart==null)return;
        fftChart.setBackgroundColor(Color.TRANSPARENT);fftChart.getDescription().setEnabled(false);
        fftChart.setDrawGridBackground(false);fftChart.setTouchEnabled(true);
        fftChart.getAxisRight().setEnabled(false);
        fftChart.getXAxis().setTextColor(0xFF4A5568);fftChart.getXAxis().setGridColor(0xFF1E2A3A);fftChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        fftChart.getAxisLeft().setTextColor(0xFF4A5568);fftChart.getAxisLeft().setGridColor(0xFF1E2A3A);
    }
    private void styleChart(LineChart c){
        if(c==null)return;
        c.setBackgroundColor(Color.TRANSPARENT);c.getDescription().setEnabled(false);c.setDrawGridBackground(false);
        c.setTouchEnabled(true);c.setDragEnabled(true);c.setScaleEnabled(true);c.setPinchZoom(true);
        c.getLegend().setTextColor(0xFFEAEEF5);c.getLegend().setTextSize(11f);
        c.getXAxis().setTextColor(0xFF4A5568);c.getXAxis().setGridColor(0xFF1E2A3A);c.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        c.getAxisLeft().setTextColor(0xFF4A5568);c.getAxisLeft().setGridColor(0xFF1E2A3A);c.getAxisLeft().setDrawZeroLine(true);c.getAxisLeft().setZeroLineColor(0xFF4A5568);
        c.getAxisRight().setEnabled(false);
    }
    private LineDataSet createDS(String label,int color){
        LineDataSet ds=new LineDataSet(new ArrayList<>(),label);
        ds.setColor(color);ds.setLineWidth(2f);ds.setDrawCircles(false);ds.setDrawValues(false);
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);ds.setCubicIntensity(0.2f);ds.setHighlightEnabled(false);
        return ds;
    }

    @Override
    public void onSensorChanged(SensorEvent event){
        frameCount++;
        switch(event.sensor.getType()){
            case Sensor.TYPE_ACCELEROMETER:{
                float x=event.values[0],y=event.values[1],z=event.values[2];
                lastAccelMag=(float)Math.sqrt(x*x+y*y+z*z);
                waveformView.addValues(x,y,z);
                if(frameCount%3==0)heatmapView.addSample(x,y,z);
                fftSamples[fftWriteHead%fftSamples.length]=lastAccelMag;fftWriteHead++;
                if(currentTab==1)pushAccel(x,y,z);
                if(currentTab==4)updateRadar();
                if(currentTab==5&&frameCount%20==0)updateFft();
                break;
            }
            case Sensor.TYPE_LIGHT:{
                lastLight=event.values[0];
                if(tvLightLux!=null)tvLightLux.setText(String.format("%.0f lx",lastLight));
                if(currentTab==2)pushLight(lastLight);
                if(currentTab==4)updateRadar();
                break;
            }
            case Sensor.TYPE_PROXIMITY:{lastProx=event.values[0];if(currentTab==4)updateRadar();break;}
        }
    }
    @Override public void onAccuracyChanged(Sensor s,int a){}

    private void pushAccel(float x,float y,float z){
        addEntry(accelLineData,0,xIndex,x);addEntry(accelLineData,1,xIndex,y);addEntry(accelLineData,2,xIndex,z);xIndex++;
        if(accelLineData.getEntryCount()>MAX_DATA_POINTS*3)for(int i=0;i<3;i++){ILineDataSet d=accelLineData.getDataSetByIndex(i);if(d!=null)d.removeFirst();}
        accelLineData.notifyDataChanged();lineChart.notifyDataSetChanged();lineChart.setVisibleXRangeMaximum(MAX_DATA_POINTS);lineChart.moveViewToX(accelLineData.getEntryCount());
    }
    private void pushLight(float lux){
        if(lightGauge!=null) lightGauge.setValue(lux);
        ILineDataSet ds=lightLineData.getDataSetByIndex(0);
        if(ds!=null){ds.addEntry(new Entry(xIndex,lux));if(ds.getEntryCount()>MAX_DATA_POINTS)ds.removeFirst();}
        lightLineData.notifyDataChanged();lightChart.notifyDataSetChanged();lightChart.setVisibleXRangeMaximum(MAX_DATA_POINTS);lightChart.moveViewToX(ds!=null?ds.getEntryCount():0);
    }
    private void addEntry(LineData ld,int i,float x,float y){ILineDataSet ds=ld.getDataSetByIndex(i);if(ds!=null)ds.addEntry(new Entry(x,y));}
    private void updateRadar(){
        RadarDataSet ds=(RadarDataSet)radarData.getDataSetByIndex(0);if(ds==null)return;
        ds.clear();
        ds.addEntry(new RadarEntry(norm(lastAccelMag,0f,20f)));
        ds.addEntry(new RadarEntry(norm(lastLight,0f,1000f)));
        ds.addEntry(new RadarEntry(norm(lastProx,0f,10f)));
        radarData.notifyDataChanged();radarChart.notifyDataSetChanged();radarChart.invalidate();
    }
    private void updateFft(){
        if(fftChart==null)return;
        float[]mags=FFTAnalyzer.computeMagnitudes(fftSamples,SAMPLE_RATE_HZ);
        int bins=Math.min(64,mags.length);
        List<BarEntry>entries=new ArrayList<>();
        for(int i=1;i<bins;i++)entries.add(new BarEntry(i,mags[i]*100f));
        BarDataSet ds=new BarDataSet(entries,"Hz");ds.setColor(0xFF00E5FF);ds.setDrawValues(false);
        BarData bd=new BarData(ds);bd.setBarWidth(0.9f);fftChart.setData(bd);fftChart.invalidate();
        float dom=FFTAnalyzer.dominantFrequency(mags,SAMPLE_RATE_HZ);
        if(tvFftInfo!=null)tvFftInfo.setText(String.format("Dominant: %.1f Hz  ·  pinch to zoom",dom));
    }
    private float norm(float v,float mn,float mx){return Math.max(0f,Math.min(1f,(v-mn)/(mx-mn)));}

    @Override public void onResume(){
        super.onResume();
        if(accel!=null)sensorManager.registerListener(this,accel,SensorManager.SENSOR_DELAY_GAME);
        if(light!=null)sensorManager.registerListener(this,light,SensorManager.SENSOR_DELAY_NORMAL);
        if(proximity!=null)sensorManager.registerListener(this,proximity,SensorManager.SENSOR_DELAY_NORMAL);
    }
    @Override public void onPause(){super.onPause();sensorManager.unregisterListener(this);}
}
