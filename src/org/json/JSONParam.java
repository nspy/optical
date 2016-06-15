package org.json;

import java.lang.reflect.Type;

import com.google.gson.Gson;

public class JSONParam {

	String valueAsJson;

	transient
	String name;

	transient
	Object value;

	protected JSONParam(){
		// For Serialization
	}

	public JSONParam(String value){
		this.valueAsJson = value;
	}

	public JSONParam(String name, Object value){
		this.value = value;
		this.name = name;
	}

	public <T> T getValue(Gson gson, Type t){
                // forcing <T> cast to work around
                // broken 1.6.0._2{1,2,3,4} openjdk javac
                // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6302954
		return gson.<T>fromJson(valueAsJson, t);
	}

	public String getName(){
		return name;
	}

	public Object getValue(){
		return value;
	}
}
