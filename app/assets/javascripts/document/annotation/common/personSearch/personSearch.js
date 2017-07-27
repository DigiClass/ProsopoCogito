define([

], function() {

  var personSearch = function() {

    var self = this,

        element = jQuery(
          '<div class="clicktrap">' +
            '<div class="modal-wrapper georesolution-wrapper">' +
              '<div class="modal georesolution-panel">' +
                '<div class="modal-header">' +
                  '<div class="georesolution-search">' +
                    '<input class="search inline" type="text" placeholder="Search for a Place..." />' +
                    '<button class="search icon">&#xf002;</button>' +
                  '</div>' +
                  '<button class="nostyle outline-icon cancel">&#xe897;</button>' +
                '</div>' +
                '<div class="modal-body">' +
                  '<div class="georesolution-sidebar">' +
                    '<div class="result-header">'+
                      '<div class="result-total">' +
                        '<span class="icon">&#xf03a;</span> <span class="label"></span>' +
                      '</div>' +
                      '<div class="result-took"></div>' +
                    '</div>' +
                    '<div class="result-list">' +
                      '<ul></ul>' +
                      '<div class="wait-for-next">' +
                        '<img src="/assets/images/wait-circle.gif">' +
                      '</div>' +
                    '</div>' +
                  '</div>' +
              '</div>' +
            '</div>' +
          '</div>'
        ).appendTo(document.body).hide(),

        open = function () {
          element.show();
        };

       this.open = open;
  };

  return personSearch;
});
