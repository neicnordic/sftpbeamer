/**
 * Created by Xiaxi Li on 25/Aug/2015.
 */
$(document).ready(function() {
    var target;
    var username;
    var hostname;
    var port;

    // Read host-specific configuration info from file.
    var host_info = [];
    $.ajax({
        type: 'GET',
        url: '/sftp/info',
        dataType: 'json',
        success: function (data) {
            host_info = data;
            server_info = host_info['server'];
        },
        async: false
    });

    // Populate the hostname inputs with values from config file
    $('#host1-hostname').val(host_info.hosts['host1']);
    $('#host2-hostname').val(host_info.hosts['host2']);

    // Define button click action
    $('.btn-connect').click(function(event) {
        event.preventDefault();
        target = $(this).attr('data-target');
        username = $('#' + target + '-username').val();
        hostname = $('#' + target + '-hostname').val();
        port = $('#' + target + '-port').val();

        if (host_info.loginmodes[hostname] == 'otp') {
            $('#credential_modal .modal-body').html('<div class="form-group"> ' +
                '<label for="password" class="sr-only">Password</label> ' +
                '<input type="password" class="form-control" id="password" placeholder="Password"> ' +
                '</div> <div class="form-group"> <label for="otc" class="sr-only">One-time Code</label> ' +
                '<input type="text" class="form-control" id="otc" placeholder="One-time Code"/> </div>');
        } else {
            $('#credential_modal .modal-body').html('<div class="form-group"> ' +
                '<label for="password" class="sr-only">Password</label> ' +
                '<input type="password" class="form-control" id="password" placeholder="Password"></div>');
        }
        $('#credential_modal').modal({
            keyboard: false,
            backdrop: true
        });
        $('#credential_modal').on('shown.bs.modal', function () {
            $('#password').focus();
        });
    });

    $('#credential_submit').click(function(event){
        event.preventDefault();
        var password;
        var otc = '';
        if (host_info.loginmodes[hostname] == 'otp') {
            otc = $('#otc').val();
        }
        password = $('#password').val();


        var requestData = {
            "username": username,
            "password": password,
            "otc": otc,
            "hostname": hostname,
            "port": port,
            "source": target
        };
        $.ajax({
            type: "POST",
            url: "/sftp/login",
            data: JSON.stringify(requestData),
            statusCode: {
                200: function (returnedData) {
                    disable_waiting_box();

                    $("#" + target + "-path").append('<a class="' + target + '-path-link" href="/sftp/list?path=' + returnedData["home"] + '&source=' + target + '">&laquo;Home&raquo;/</a>');
                    $("#" + target + "-table-div").html('<table id="' + target + '-table" class="table table-striped"></table>');
                    createTable(target, returnedData["home"], returnedData["data"]);
                    $("#" + target + "-disconnect-btn").css("display", "inline-block");
                    $("#" + target + "-submit-btn").css("display", "none");
                    $("#" + target + "-username").prop("disabled", true);
                    $("#" + target + "-hostname").prop("disabled", true);
                    $("#" + target + "-port").prop("disabled", true);
                }
            },
            error: function (jqXhR, textStatus, errorThrown) {
                disable_waiting_box();
                showErrorAlertInTop(target, jqXhR.responseText, errorThrown, 'Login failed.')
            },
            dataType: "json",
            contentType: 'application/json; charset=utf-8'
        });
        enable_waiting_box("Connecting");
    });
});
