package com.tencent.bk.devops.atom.task.api;

import com.tencent.bk.devops.atom.api.BaseApi;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class HttpUtil extends BaseApi {

    private Logger logger = LoggerFactory.getLogger(HttpUtil.class);


    private static OkHttpClient client = new OkHttpClient
            .Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public String doGet(String path) throws IOException {
        Request request = super.buildGet(path);
        Call call = client.newCall(request);
        Response response = call.execute();
        logger.error(String.format("request acrossProjectCopy, response:%S", response));
        if(response.isSuccessful()){
            return response.body().string();
        }else{
            return "false";
        }
    }


}