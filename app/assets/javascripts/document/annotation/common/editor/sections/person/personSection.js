define([
  'common/ui/formatting',
  'common/config',
  'document/annotation/common/editor/sections/section'
], function(Formatting, Config, Section) {

  var PersonSection = function(parent, personBody, personName) {
    var self = this,

        /** Changes to be applied to the annotation when the user clicks OK **/
        queuedUpdates = [],

        // TODO clean up
        element = (function() {
          var lastModified = jQuery(
                '<div class="last-modified">' +
                  '<a class="by" href="/' + personBody.last_modified_by + '">' +
                    personBody.last_modified_by + '</a>' +
                  '<span class="at">' +
                    Formatting.timeSince(personBody.last_modified_at) +
                  '</span>' +
                '</div>'),

              el = jQuery(
                '<div class="section category person">' +
                  '<div class="category-icon">&#xe863;</div>' +
                  '<div class="info">' +
                    '<div>Marked as a Person</div>' +
                    '<div class="uri"></div>' +
                  '</div>' +
                '</div>'),

              infoEl = el.find('.info');

          if (personBody.uri)
            infoEl.find('.uri').html(
              '<a target="_blank" href="' + personBody.uri + '">' + personBody.uri + '</a>');

          if (personBody.last_modified_at)
            infoEl.append(lastModified);

          if (Config.writeAccess)
            infoEl.append(
              '<button class="change btn tiny icon">&#xf040;</button>' +
              '<button class="delete btn tiny icon">&#xf014;</button>');

          parent.append(el);
          return el;
        })(),

        /**
         * Updates the section with a change performed by the user
         * TODO this is pretty much identical to placeSection - move into common base class!
         */
        update = function(diff) {
          var lastModified = { by: Config.me, at: new Date() };

          if (personBody.uri !== diff.uri && diff.uri)
            element.find('.info .uri').html(
              '<a target="_blank" href="' + diff.uri + '">' + diff.uri + '</a>');

          // Queue these updates for deferred storage
          queuedUpdates.push(function() {
            delete personBody.last_modified_by;
            delete personBody.last_modified_at;
            if (diff.uri)
              personBody.uri = diff.uri;
            else
              delete personBody.uri;
          });
        },

        /** TODO identical to placeSection - move into common base class **/
        commit = function() {
          jQuery.each(queuedUpdates, function(idx, fn) { fn(); });
        },

        /** TODO identical to placeSection - move into common base class **/
        hasChanged = function() {
          return queuedUpdates.length > 0;
        },

        destroy = function() {
          element.remove();
        };

    element.on('click', '.delete', function() { self.fireEvent('delete'); });
    element.on('click', '.change', function() { self.fireEvent('searchPerson'); });

    this.body = personBody;
    this.update = update;
    this.destroy = destroy;
    this.hasChanged = hasChanged;
    this.commit = commit;

    Section.apply(this);
  };
  PersonSection.prototype = Object.create(Section.prototype);

  return PersonSection;

});
