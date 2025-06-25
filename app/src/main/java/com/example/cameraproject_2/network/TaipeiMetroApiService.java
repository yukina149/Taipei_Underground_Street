package com.example.cameraproject_2.network;
import com.example.cameraproject_2.model.MetroApiResponse; // 你的模型包名
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query; // <<<<< 確保引入的是這個 retrofit2.http.Query

public interface TaipeiMetroApiService {
    @GET("api/v1/dataset/893c2f2a-dcfd-407b-b871-394a14105532?scope=resourceAquire")
    Call<MetroApiResponse> getMetroFares(
            @Query("limit") int limit,         // <<<<< 這裡直接使用 @Query
            @Query("offset") int offset        // <<<<< 這裡直接使用 @Query
    );
}
