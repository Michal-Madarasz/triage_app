package com.triage.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

//klasa reprezentująca poszkodowanego rozszerzona o
//interfejs pozwalający na przesyłanie obiektu między aktywnościami
public class Victim implements Parcelable, Serializable {
    private static final long serialVersionUID = 186362213453111235L;

    public static final Creator<Victim> CREATOR = new Creator<Victim>() {
        @Override
        public Victim createFromParcel(Parcel in) {
            return new Victim(in);
        }

        @Override
        public Victim[] newArray(int size) {
            return new Victim[size];
        }
    };
    private long transmitterIMEI;
    private boolean breathing;
    private int respiratoryRate;
    private float capillaryRefillTime;
    private boolean walking;
    private TriageColor color;
    private AVPU consciousness;

    public Victim(long transmitterIMEI, boolean breathing, int respiratoryRate, float capillaryRefillTime, boolean walking, AVPU consciousness) {
        this.transmitterIMEI = transmitterIMEI;
        this.breathing = breathing;
        this.respiratoryRate = respiratoryRate;
        this.capillaryRefillTime = capillaryRefillTime;
        this.walking = walking;
        this.consciousness = consciousness;
        calculateColor();
    }

    protected Victim(Parcel in) {
        transmitterIMEI = in.readLong();
        breathing = (boolean) in.readValue(null);
        respiratoryRate = in.readInt();
        capillaryRefillTime = in.readFloat();
        walking = (boolean) in.readValue(null);
        color = (TriageColor) in.readValue(null);
        consciousness = (AVPU) in.readValue(null);
    }

    public void calculateColor() {
        if (walking) {
            color = TriageColor.GREEN;
            return;
        }

        if (!breathing) {
            color = TriageColor.BLACK;
            return;
        }

        if (respiratoryRate > 30) {
            color = TriageColor.RED;
            return;
        }

        if (capillaryRefillTime > 2) {
            color = TriageColor.RED;
            return;
        }

        if (consciousness == AVPU.PAIN || consciousness == AVPU.UNRESPONSIVE) {
            color = TriageColor.RED;
            return;
        }
        color = TriageColor.YELLOW;
    }

    public long getTransmitterIMEI() {
        return transmitterIMEI;
    }

    public void setTransmitterIMEI(long transmitterIMEI) {
        this.transmitterIMEI = transmitterIMEI;
    }

    public boolean isBreathing() {
        return breathing;
    }

    public void setBreathing(boolean breathing) {
        this.breathing = breathing;
    }

    public int getRespiratoryRate() {
        return respiratoryRate;
    }

    public void setRespiratoryRate(int respiratoryRate) {
        this.respiratoryRate = respiratoryRate;
    }

    public boolean isWalking() {
        return walking;
    }

    public void setWalking(boolean walking) {
        this.walking = walking;
    }

    public TriageColor getColor() {
        return color;
    }

    public void setColor(TriageColor color) {
        this.color = color;
    }

    public AVPU getConsciousness() {
        return consciousness;
    }

    public void setConsciousness(AVPU consciousness) {
        this.consciousness = consciousness;
    }

    public float getCapillaryRefillTime() {
        return capillaryRefillTime;
    }

    public void setCapillaryRefillTime(float capillaryRefillTime) {
        this.capillaryRefillTime = capillaryRefillTime;
    }

    //Parceable methods
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(transmitterIMEI);
        dest.writeValue(breathing);
        dest.writeInt(respiratoryRate);
        dest.writeFloat(capillaryRefillTime);
        dest.writeValue(walking);
        dest.writeValue(color);
        dest.writeValue(consciousness);
    }

    public enum TriageColor {BLACK, RED, YELLOW, GREEN}

    public enum AVPU {AWAKE, VERBAL, PAIN, UNRESPONSIVE}
}
