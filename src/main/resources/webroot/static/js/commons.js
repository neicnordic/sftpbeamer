/**
 * Created by xiaxi on 01/05/16.
 */
$(document).ready(function () {
    window.addEventListener("beforeunload", function (e) {
        logout();
        return null;
    });

    $("#host2-disconnect-btn").css("display", "none");
    $("#host1-disconnect-btn").css("display", "none");

    $("html").on("click", function(){
        $(".host1-menu").hide();
        $(".host2-menu").hide();
    });


    $('#upload-submit').click(function (event) {
        if(uploaded_files_array.length > 0) {
            uploaded_files_array.pop().submit();
        }
        $(this).attr('disabled' , true);
    });

    $("input[type=file]").bind("change", function(event) {
        var selected_file_number = this.files.length;
        if (selected_file_number > 0) {
            $("#upload-submit").attr('disabled' , false);
        } else {
            /* No file selected or cancel/close
             dialog button clicked */
            /* If user has select a file before,
             when they submit, it will treated as
             no file selected */
        }
    });

    var upload_input = $('#upload_input');

    upload_input.click(function () {
        var upload_progress_group = $('#upload_progress_group');
        if (upload_progress_group.children().length != 0) {
            upload_progress_group.empty();
            uploaded_files_array = [];
            progress_bar_group = {};
        }
    });

    upload_input.fileupload({
        maxChunkSize: 10000000000,
        multipart: false,
        type: 'PUT',
        url: '',
        dataType: 'json',
        add: function (e, data) {
            data.url = "/sftp/upload?path=" + extractPath($("." + upload_target + "-path-link").last().attr("href")) + "&source=" + upload_target;
            var fileName = data.files[0].name;
            var upload_progress_group = $('#upload_progress_group');
            var progress_bar_index = upload_progress_group.children().length + 1;
            upload_progress_group.append('<div class="progress" style="margin-bottom: 10px;"> <div class="progress-bar progress-bar-info progress-bar-striped" role="progressbar" aria-valuemin="0" aria-valuemax="100"><span style="color: black;font-size: medium;">' + fileName +'</span> </div></div>');
            progress_bar_group[fileName] = progress_bar_index;
            uploaded_files_array.push(data);
        },
        progress: function (e, data) {
            var progress = parseInt(data.loaded / data.total * 100, 10);
            var fileName = data.files[0].name;
            var progress_bar_index = progress_bar_group[fileName];
            $('#upload_progress_group div:nth-child(' + progress_bar_index + ') .progress-bar').css(
                'width',
                progress + '%'
            );
            $('#upload_progress_group div:nth-child(' + progress_bar_index + ') .progress-bar span').text(progress + '% ' + fileName);
        },
        always: function (e, data) {
            if(uploaded_files_array.length > 0) {
                uploaded_files_array.pop().submit();
            }
        }
    });

    $('#upload_modal').on('hide.bs.modal', function () {
        refresh_target_host(upload_target);
    });

    $('#transfer_modal').on('hide.bs.modal', function () {
        refresh_target_host(transfer_target);
    });
    
    $('#folder_create').click(function (event) {
        event.preventDefault();
        var folder_name = $('#folder_name').val();
        var path = create_folder_path + "/" + folder_name;
        var request_data = {"source": create_folder_target, "path": path};
        if (folder_name) {
            $.ajax({
                type: "POST",
                url: "/sftp/create",
                data: JSON.stringify(request_data),
                contentType: 'application/json; charset=utf-8',
                statusCode: {
                    201: function () {
                        refresh_target_host(create_folder_target);
                    }
                },
                error: function (jqXHR, textStatus, errorThrown) {
                    showErrorAlertInTop(create_folder_target, jqXHR.responseText, errorThrown, 'Folder creation failed.');
                }
            });
        }
    });

    $('#confirm_rename').click(function (event) {
        event.preventDefault();
        var new_name = $('#new_name').val();
        var request_data = {"source": rename_target, "path": rename_path, "old_name": old_name, "new_name": new_name};
        if (new_name) {
            $.ajax({
                type: "POST",
                url: "/sftp/rename",
                data: JSON.stringify(request_data),
                contentType: 'application/json; charset=utf-8',
                statusCode: {
                    200: function () {
                        refresh_target_host(rename_target);
                    }
                },
                error: function (jqXHR, textStatus, errorThrown) {
                    showErrorAlertInTop(rename_target, jqXHR.responseText, errorThrown, 'Renaming failed.');
                }
            });
        }
    });

});