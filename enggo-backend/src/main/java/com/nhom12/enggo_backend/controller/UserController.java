package com.nhom12.enggo_backend.controller;

import com.nhom12.enggo_backend.dto.request.ApiResponse;
import com.nhom12.enggo_backend.dto.request.UpdatePasswordRequest;
import com.nhom12.enggo_backend.dto.request.UserCreationRequest;
import com.nhom12.enggo_backend.dto.request.UserUpdateRequest;
import com.nhom12.enggo_backend.dto.response.PageResponse;
import com.nhom12.enggo_backend.dto.response.UserMinimalResponse;
import com.nhom12.enggo_backend.dto.response.UserResponse;
import com.nhom12.enggo_backend.service.UserService;
import com.nhom12.enggo_backend.service.upload.UploadsService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class UserController {
    @GetMapping("/top-elo")
    ApiResponse<com.nhom12.enggo_backend.dto.response.TopUsersResponse> getTopElo() {
        return ApiResponse.<com.nhom12.enggo_backend.dto.response.TopUsersResponse>builder()
                .result(userService.getTopElo())
                .build();
    }
    @GetMapping("/leader_board")
    public ApiResponse<PageResponse<UserResponse>> getLeaderBoard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        // Cấu hình phân trang: Sắp xếp theo Elo giảm dần
        Pageable pageable = PageRequest.of(page -1, size, Sort.by("elo").descending());

        return ApiResponse.<PageResponse<UserResponse>>builder()
                .result(userService.getLeaderBoard(pageable))
                .build();
    }
    @Autowired
    UserService userService;
    UploadsService uploadsService;

    @PostMapping("/signup")
    ApiResponse<UserResponse> createUser(@RequestBody @Valid UserCreationRequest request) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.createUser(request))
                .build();
    }

    @GetMapping
    ApiResponse<List<UserResponse>> getUsers() {
        return ApiResponse.<List<UserResponse>>builder()
                .result(userService.getUsers())
                .build();
    }

    @GetMapping("/{userId}")
    ApiResponse<UserResponse> getUser(@PathVariable("userId") Integer userId) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.getUser(userId))
                .build();
    }

    @GetMapping({"/my-info", "/me"})
    ApiResponse<UserResponse> getMyInfo() {
        return ApiResponse.<UserResponse>builder()
                .result(userService.getMyInfo())
                .build();
    }

    @GetMapping("/search")
    ApiResponse<List<UserMinimalResponse>> searchUsers(@RequestParam("username") String username) {
        return ApiResponse.<List<UserMinimalResponse>>builder()
                .result(userService.searchUsersByUsername(username))
                .build();
    }

    @DeleteMapping("/{userId}")
    ApiResponse<String> deleteUser(@PathVariable Integer userId) {
        userService.deleteUser(userId);
        return ApiResponse.<String>builder().result("User has been deleted").build();
    }

    @PutMapping({"/my-info","/me"})
    ApiResponse<UserResponse> updateUser(@RequestBody UserUpdateRequest request) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.updateUser(request))
                .build();
    }

    @PutMapping("/updatePassword")
    ApiResponse<String> updateUserPassword(@RequestBody UpdatePasswordRequest request) {
        userService.updateUserPassword(request);
        return ApiResponse.<String>builder()
                .result("Password has been changed")
                .build();
    }

    @PostMapping("/uploadAvatar")
    public ApiResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        System.out.println("đã xuống đến đây");
        // 1. Upload ảnh lên Cloud
        String imageUrl = uploadsService.uploadImage(file);

        // 2. Cập nhật đường dẫn ảnh vào Database cho User tương ứng
        userService.updateAvatar(imageUrl);

        return ApiResponse.<String>builder()
                .result(imageUrl)
                .message("Cập nhật ảnh đại diện thành công")
                .build();
    }
}
