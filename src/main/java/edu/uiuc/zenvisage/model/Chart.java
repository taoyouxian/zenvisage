package edu.uiuc.zenvisage.model;

import java.util.ArrayList;

public class Chart {
	public String xType;
	public String yType;
	public String zType;
	public String title;
	public ArrayList<String> xData = new ArrayList<String>();
	public ArrayList<String> yData = new ArrayList<String>();
	public int count;
	public int rank;
	public double distance;
	
	// default constructor
	public Chart() {
		
	}
	public String getxType() {
		return xType;
	}
	public void setxType(String xType) {
		this.xType = xType;
	}
	public String getyType() {
		return yType;
	}
	public void setyType(String yType) {
		this.yType = yType;
	}
	public String getzType() {
		return zType;
	}
	public void setzType(String zType) {
		this.zType = zType;
	}
	public ArrayList<String> getxData() {
		return xData;
	}
	public void setxData(ArrayList<String> xData) {
		this.xData = xData;
	}
	public ArrayList<String> getyData() {
		return yData;
	}
	public void setyData(ArrayList<String> yData) {
		this.yData = yData;
	}
	public void setRank(int rank){
		this.rank = rank;
	}
	public int getRank(){
		return this.rank;
	}
	
	
	public void setDistance(double d) {
		this.distance = d;
	}
	public double getDistance() {
		return this.distance;
	}
	
}
