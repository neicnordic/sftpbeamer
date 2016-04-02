/**
 * Created by Xiaxi Li on 26/Aug/2015.
 */
var host1_table;
var host2_table;

function createTable(name, content) {
    if (name == 'host1') {
        host1_table = $("#host1-table").dataTable({
            "pagingType": "simple",
            "dom": "tlp",
            "language": {
                "paginate": {
                    "previous": "&laquo;",
                    "next": "&raquo;"
                }
            },
            "info": false,
            "searching": false,
            "lengthMenu": [[10, 25, 40, -1], [10, 25, 40, "All"]],
            "data": content,
            "columns": [{
                "title": "Name",
                "render": function (data, type, full, meta) {
                    var isFoler = full[2];
                    if (isFoler == "folder") {
                        return '<i class="fa fa-folder-o"></i> <a class="host1-folder-link" href="/sftp/list?path=/' + data + '&source=host1"> ' + data + '</a>';
                    } else {
                        return '<i class="fa fa-file-o"></i> ' + data;
                    }
                }
            }, {
                "title": "Size"
            }, {
                "visible": false
            }]
        });
    } else if (name == 'host2') {
        host2_table = $("#host2-table").dataTable({
            "pagingType": "simple",
            "dom": "tlp",
            "language": {
                "paginate": {
                    "next": "&gt;",
                    "previous": "&lt;"
                }
            },
            "info": false,
            "searching": false,
            "lengthMenu": [[10, 25, 40, -1], [10, 25, 40, "All"]],
            "data": content,
            "columns": [{
                "title": "Name",
                "render": function (data, type, full, meta) {
                    var isFolder = full[2];
                    if (isFolder == "folder") {
                        return '<i class="fa fa-folder-o"></i> <a class="host2-folder-link" href="/sftp/list?path=/' + data + '&source=host2"> ' + data + '</a>';
                    } else {
                        return '<i class="fa fa-file-o"></i> ' + data;
                    }
                }
            }, {
                "title": "Size"
            }, {
                "visible": false
            }]
        });
    }
}

function getCookie(name) {
    var cookieValue = null;
    if (document.cookie && document.cookie != '') {
        var cookies = document.cookie.split(';');
        for (var i = 0; i < cookies.length; i++) {
            var cookie = jQuery.trim(cookies[i]);
            if (cookie.substring(0, name.length + 1) == (name + '=')) {
                cookieValue = decodeURIComponent(cookie.substring(name.length + 1));
                break;
            }
        }
    }
    return cookieValue;
}

function enable_waiting_box(message) {
    $('#gray-screen').css({'display': 'block', opacity: 0.3, 'width': $(document).width(), 'height': $(document).height()});
    $('body').css({'overflow': 'hidden'});
    $('#waiting-box').css({'display': 'block'});
    $('#waiting-box > p').text(message);
}

function disable_waiting_box() {
    $('#gray-screen').css({'display': 'none'});
    $('#waiting-box').css({'display': 'none'});
    $('body').css({'overflow':'auto'});
}

function create_ws_connection() {
    // This is for local testing
    var endpoint = 'ws://localhost:8081/ws';
    // This is for testing server without ssl
    // var endpoint = 'ws://tryggve.cbu.uib.no:80/websocket';
    //This is for testing server with ssl
    //var endpoint = 'wss://tryggve.cbu.uib.no:443/websocket';

    return new WebSocket(endpoint);
}

function refresh_progress_bar(message) {
    var transferred_file_name = $('#transferred-file-name');
    var progress_bar = $('#transferred-file-progress-bar');

    transferred_file_name.text(message["file_name"]);
    var transferred_bytes = Number(message["transferred_bytes"]);
    var total_bytes = Number(message["total_bytes"]);
    if (transferred_bytes == total_bytes) {
        progress_bar.text('100%');
        progress_bar.css("width", '100%');
    } else {
        var percentage = Math.round(transferred_bytes / total_bytes * 100);
        progress_bar.text(percentage + '%');
        progress_bar.css("width", percentage + '%');
    }
}

function change_modal_property(modal_title, modal_content) {
    $('#info_modal_label').text(modal_title);

    $('.modal-body p').text(modal_content);
}

function fetch_table(source) {
    if (source == 'host1') {
        return host1_table;
    }
    if (source == 'host2') {
        return host2_table;
    }
}

function set_table(source, table) {
    if (source == 'host1') {
        host1_table = table;
    }
    if (source == 'host2') {
        host2_table = table;
    }
}

// str generateId(int len);
//   len - must be an even number (default: 40)
function generateId(len) {
    var arr = new Uint8Array((len || 40) / 2);
    window.crypto.getRandomValues(arr);
    return [].map.call(arr, function(n) { return n.toString(16); }).join("");
}

function extractPath(href) {
    var path = href.substring(href.indexOf("?") + 1, href.indexOf("&"));
    return path.substring(path.indexOf("=") + 1);
}
