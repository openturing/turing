import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { TurNLPEntity } from '../../model/nlp-entity.model';
import { NotifierService } from 'angular-notifier';
import { TurNLPEntityService } from '../../service/nlp-entity.service';
import { ActivatedRoute, Router } from '@angular/router';
import { FormControl, Validators } from '@angular/forms';

@Component({
  selector: 'nlp-entity-page',
  templateUrl: './nlp-entity-page.component.html'
})
export class TurNLPEntityPageComponent implements OnInit {
  @ViewChild('modalDelete') modalDelete: ElementRef;
  private turNLPEntity: Observable<TurNLPEntity>;

  portControl = new FormControl(80, [Validators.max(100), Validators.min(0)])


  constructor(private readonly notifier: NotifierService,
    private turNLPEntityService: TurNLPEntityService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {
    let id = this.activatedRoute.snapshot.paramMap.get('id');
    this.turNLPEntity = this.turNLPEntityService.get(id);

  }

  getTurNLPEntity(): Observable<TurNLPEntity> {
    return this.turNLPEntity;
  }

  ngOnInit(): void {
  }

  public save(_turNLPEntity: TurNLPEntity) {
    this.turNLPEntityService.save(_turNLPEntity).subscribe(
      (turNLPEntity: TurNLPEntity) => {
        _turNLPEntity = turNLPEntity;
        this.notifier.notify("success", turNLPEntity.name.concat(" NLP entity was updated."));
      },
      response => {
        this.notifier.notify("error", "NLP entity was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });

  }

  public delete(_turNLPEntity: TurNLPEntity) {
    this.turNLPEntityService.delete(_turNLPEntity).subscribe(
      (turNLPEntity: TurNLPEntity) => {
        this.notifier.notify("success", _turNLPEntity.name.concat(" NLP entity was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");
        this.router.navigate(['/console/nlp/entity']);
      },
      response => {
        this.notifier.notify("error", "NLP entity was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });

  }
}
